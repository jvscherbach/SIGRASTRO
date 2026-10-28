import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, Subject, of, throwError } from 'rxjs';

import { ConteudoApiService, DocumentosApiService, DossiesApiService, LIMITE_DO_BLOB_MB } from './documentos-api.service';
import { DocumentosSecaoComponent } from './documentos-secao.component';
import { documento } from './documentos.fixtures';
import { ConteudoRecebido, DocumentoItem, DocumentosResposta, GeometriaItem } from './documentos.models';

function resposta(itens: DocumentoItem[]): DocumentosResposta {
  return {
    itens,
    totais: {
      total: itens.length,
      vigentes: itens.filter((d) => d.situacao === 'VIGENTE').length,
      semEfeito: itens.filter((d) => d.situacao === 'SUBSTITUIDO').length,
      naoPdf: itens.filter((d) => d.bloqueio === 'NAO_PDF').length,
      tipoNaoInformado: itens.filter((d) => d.tipoArquivo === 'DESCONHECIDO').length,
      indisponiveis: itens.filter((d) => d.situacao === 'INDISPONIVEL').length,
      sigilosos: itens.filter((d) => d.sigiloso).length,
      convertiveis: itens.filter((d) => d.bloqueio === null && ['PLANILHA', 'TEXTO', 'IMAGEM'].includes(d.tipoArquivo ?? '')).length,
    },
  };
}

const PLANILHA = (sobre: Partial<DocumentoItem> = {}) =>
  documento({ id: '4', titulo: 'Cálculo', nomeArquivo: 'calculo.xlsx', extensao: 'xlsx', tipoArquivo: 'PLANILHA', bloqueio: 'NAO_PDF', selecionavelPorPadrao: false, data: '2025-04-04T10:00:00', ...sobre });
const INDISPONIVEL = documento({ id: '5', titulo: 'Perdido', nomeArquivo: 'perdido.pdf', situacao: 'INDISPONIVEL', bloqueio: 'INDISPONIVEL', selecionavelPorPadrao: false, data: '2025-04-05T10:00:00' });
const SUBSTITUIDO = documento({ id: '6', titulo: 'Antigo', nomeArquivo: 'antigo.pdf', origem: 'DOCUMENTO_REQUERIMENTO', origemDescricao: 'Documento do requerimento', situacao: 'SUBSTITUIDO', data: '2025-03-30T10:00:00' });
const SIGILOSO_MASCARADO = documento({
  id: '7',
  titulo: 'Documento sigiloso',
  nomeArquivo: null,
  extensao: null,
  tipoArquivo: null,
  sigiloso: true,
  acessivel: false,
  bloqueio: 'SIGILOSO',
  selecionavelPorPadrao: false,
  data: '2025-04-03T10:00:00',
});

const BASE: DocumentoItem[] = [
  documento({ id: '1', titulo: 'Requerimento assinado', nomeArquivo: 'requerimento.pdf', data: '2025-04-01T10:00:00' }),
  documento({ id: '2', titulo: 'Ofício', nomeArquivo: 'oficio.pdf', origem: 'JUNTADA', origemDescricao: 'Juntada', data: '2025-04-02T10:00:00' }),
  PLANILHA(),
  INDISPONIVEL,
  SUBSTITUIDO,
  SIGILOSO_MASCARADO,
];

describe('DocumentosSecaoComponent', () => {
  let api: { listar: ReturnType<typeof vi.fn> };
  let conteudoApi: ReturnType<typeof vi.fn>;
  let fixture: ComponentFixture<DocumentosSecaoComponent>;
  let raiz: HTMLElement;

  function abrir(itens: DocumentoItem[] = BASE, ajuste: (r: DocumentosResposta) => DocumentosResposta = (r) => r): void {
    api.listar.mockReturnValue(of(ajuste(resposta(itens))));
    TestBed.configureTestingModule({
      imports: [DocumentosSecaoComponent],
      providers: [
        { provide: DocumentosApiService, useValue: api },
        { provide: ConteudoApiService, useValue: { conteudo: conteudoApi } },
        // a gaveta cria o pedido ao abrir; aqui ela só precisa existir
        { provide: DossiesApiService, useValue: { criar: vi.fn(() => NEVER), andamento: vi.fn(), cancelar: vi.fn(), arquivo: vi.fn() } },
      ],
    });
    fixture = TestBed.createComponent(DocumentosSecaoComponent);
    fixture.componentRef.setInput('idRequerimento', 101);
    fixture.detectChanges();
    raiz = fixture.nativeElement as HTMLElement;
  }

  const texto = () => raiz.textContent!.replace(/\s+/g, ' ');
  const linhas = () => Array.from(raiz.querySelectorAll<HTMLTableRowElement>('tbody tr'));
  const linhaDe = (titulo: string) => linhas().find((l) => l.textContent!.includes(titulo))!;
  const caixa = (linha: HTMLElement) => linha.querySelector<HTMLInputElement>('input[type=checkbox]')!;
  const marcados = () => linhas().filter((l) => caixa(l).checked).map((l) => l.querySelector('b')!.textContent);
  const selecionadosNaBarra = () => raiz.querySelector('.selbar b')!.textContent;
  const botao = (rotulo: string) =>
    Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes(rotulo))!;

  function digitar(valor: string): void {
    const campo = raiz.querySelector<HTMLInputElement>('.toolbar input')!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    api = { listar: vi.fn() };
    conteudoApi = vi.fn();
    URL.createObjectURL = vi.fn(() => 'blob:http://localhost/doc-1');
    URL.revokeObjectURL = vi.fn();
  });

  describe('carregamento', () => {
    it('carrega os documentos do requerimento da tela', () => {
      abrir();

      expect(api.listar).toHaveBeenCalledWith(101);
      expect(linhas().length).toBe(6);
    });

    it('mostra "Carregando" enquanto espera', () => {
      api.listar.mockReturnValue(NEVER);
      TestBed.configureTestingModule({ imports: [DocumentosSecaoComponent], providers: [{ provide: DocumentosApiService, useValue: api }] });
      fixture = TestBed.createComponent(DocumentosSecaoComponent);
      fixture.componentRef.setInput('idRequerimento', 101);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Carregando documentos');
    });

    it('erro: mensagem pelo errorCode, sem o detail técnico, e botão para tentar de novo', () => {
      api.listar.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 503, error: { errorCode: 'SIGA_INDISPONIVEL', detail: 'ORA-00942 tabela X' } })),
      );
      TestBed.configureTestingModule({ imports: [DocumentosSecaoComponent], providers: [{ provide: DocumentosApiService, useValue: api }] });
      fixture = TestBed.createComponent(DocumentosSecaoComponent);
      fixture.componentRef.setInput('idRequerimento', 101);
      fixture.detectChanges();
      raiz = fixture.nativeElement;

      expect(texto()).toContain('O banco do SIGA está indisponível no momento.');
      expect(texto()).not.toContain('ORA-00942');
      expect(raiz.querySelector('table')).toBeNull();
      expect(botao('Tentar novamente')).toBeTruthy();
    });

    it('requerimento sem documentos', () => {
      abrir([]);

      expect(texto()).toContain('Este requerimento não tem documentos');
    });
  });

  describe('lista e bloqueios', () => {
    beforeEach(() => abrir());

    it('padrão: ordem cronológica crescente', () => {
      expect(linhas().map((l) => l.querySelector('b')!.textContent)).toEqual([
        'Antigo',
        'Requerimento assinado',
        'Ofício',
        'Documento sigiloso',
        'Cálculo',
        'Perdido',
      ]);
    });

    it('mostra id, nome do arquivo, origem e data (sem coluna de situação)', () => {
      const linha = linhaDe('Requerimento assinado');

      expect(linha.textContent).toContain('ID 1');
      expect(linha.textContent).toContain('requerimento.pdf');
      expect(linha.textContent).toContain('Documento do processo');
      expect(linha.textContent).toContain('01/04/2025');
      expect(raiz.querySelector('thead')!.textContent).not.toMatch(/situa/i);
    });

    it('indisponível: esmaecido, sem seleção e com "arquivo indisponível" junto ao nome', () => {
      const linha = linhaDe('Perdido');

      expect(caixa(linha).disabled).toBe(true);
      expect(caixa(linha).checked).toBe(false);
      expect(linha.classList).toContain('off');
      expect(linha.textContent).toContain('arquivo indisponível');
    });

    it('não PDF sem conversão: bloqueado com "conversão indisponível" e aviso no topo, sem prometer próxima etapa', () => {
      const linha = linhaDe('Cálculo');

      expect(caixa(linha).disabled).toBe(true);
      expect(linha.textContent).toContain('conversão indisponível');
      const aviso = raiz.querySelector('.nao-pdf')!.textContent!;
      expect(aviso).toContain('1 documento(s) não são PDF e não têm conversão disponível');
      expect(aviso).not.toContain('próxima etapa');
      expect(raiz.querySelector('.convertiveis')).toBeNull();
    });

    it('convertíveis (conversão ligada): vêm selecionados, com etiqueta e aviso "serão convertidos para PDF"', () => {
      TestBed.resetTestingModule();
      const convertivel = PLANILHA({ bloqueio: null, selecionavelPorPadrao: true });
      const imagem = documento({ id: '9', titulo: 'Foto', nomeArquivo: 'foto.png', extensao: 'png', tipoArquivo: 'IMAGEM', data: '2025-04-09T10:00:00' });
      abrir([BASE[0], convertivel, imagem]);

      expect(marcados()).toEqual(['Requerimento assinado', 'Cálculo', 'Foto']);
      expect(linhaDe('Cálculo').textContent).toContain('será convertido para PDF');
      expect(linhaDe('Requerimento').textContent).not.toContain('será convertido');
      expect(raiz.querySelector('.convertiveis')!.textContent).toContain('2 documento(s) serão convertidos para PDF');
      expect(raiz.querySelector('.nao-pdf')).toBeNull();
    });

    it('tipo não informado: etiqueta, selecionável, já selecionado, linha própria no aviso e sem contar como não PDF', () => {
      TestBed.resetTestingModule();
      const semTipo = documento({ id: '8', titulo: 'Planta', nomeArquivo: null, extensao: '', tipoArquivo: 'DESCONHECIDO', data: '2025-04-08T10:00:00' });
      abrir([BASE[0], semTipo]);
      const linha = linhaDe('Planta');

      expect(linha.textContent).toContain('tipo não informado');
      expect(caixa(linha).disabled).toBe(false);
      expect(caixa(linha).checked).toBe(true);
      caixa(linha).click();
      fixture.detectChanges();
      expect(caixa(linhaDe('Planta')).checked).toBe(false);
      expect(raiz.querySelector('.tipo-nao-informado')!.textContent).toContain('1 documento(s) sem tipo informado: o tipo é conferido ao gerar o dossiê');
      expect(raiz.querySelector('.nao-pdf')).toBeNull();
    });

    it('sem documentos não PDF não há aviso', () => {
      TestBed.resetTestingModule();
      abrir([BASE[0]]);

      expect(raiz.querySelector('.nao-pdf')).toBeNull();
    });

    it('sigiloso sem perfil: visível, mascarado, sem seleção e com "documento sigiloso"', () => {
      const linha = linhaDe('Documento sigiloso');

      expect(caixa(linha).disabled).toBe(true);
      expect(linha.textContent).toContain('documento sigiloso');
      expect(linha.textContent).toContain('Requer perfil autorizado');
      expect(linha.textContent).not.toContain('laudo');
      expect(linha.querySelector('.lock')).not.toBeNull();
    });

    it('substituído em PDF: vem selecionado e pode ser desmarcado', () => {
      const linha = linhaDe('Antigo');

      expect(caixa(linha).checked).toBe(true);
      expect(caixa(linha).disabled).toBe(false);
      expect(linha.textContent).toContain('versão substituída');
      caixa(linha).click();
      fixture.detectChanges();
      expect(caixa(linhaDe('Antigo')).checked).toBe(false);
    });

    it('"Visualizar" em toda linha, menos nos indisponíveis', () => {
      const comAcao = (titulo: string) => linhaDe(titulo).querySelector('button.visualizar');

      expect(comAcao('Requerimento assinado')).not.toBeNull();
      expect(comAcao('Cálculo')).not.toBeNull();
      expect(comAcao('Antigo')).not.toBeNull();
      expect(comAcao('Documento sigiloso')).not.toBeNull();
      expect(comAcao('Perdido')).toBeNull();
    });
  });

  describe('seleção', () => {
    beforeEach(() => abrir());

    it('começa com todos os selecionáveis marcados (vigentes e sem efeito)', () => {
      expect(marcados()).toEqual(['Antigo', 'Requerimento assinado', 'Ofício']);
      expect(selecionadosNaBarra()).toBe('3');
    });

    it('totais por situação somam o total (vigentes + sem efeito + indisponíveis)', () => {
      expect(raiz.querySelector('.dash-sec-head p')!.textContent).toContain(
        '6 documento(s) · 4 vigente(s) · 1 sem efeito · 1 indisponível(is)',
      );
    });

    it('seleção individual atualiza o contador', () => {
      caixa(linhaDe('Antigo')).click();
      fixture.detectChanges();

      expect(selecionadosNaBarra()).toBe('2');
      caixa(linhaDe('Ofício')).click();
      fixture.detectChanges();
      expect(selecionadosNaBarra()).toBe('1');
    });

    it('"Limpar seleção" zera e "Seleção padrão" volta a marcar todos os selecionáveis', () => {
      botao('Limpar seleção').click();
      fixture.detectChanges();
      expect(marcados()).toEqual([]);
      expect(botao('Gerar dossiê').disabled).toBe(true);

      caixa(linhaDe('Antigo')).click();
      fixture.detectChanges();
      botao('Seleção padrão').click();
      fixture.detectChanges();
      expect(marcados()).toEqual(['Antigo', 'Requerimento assinado', 'Ofício']);
    });

    it('selecionar todos marca só o que pode ser selecionado (não os bloqueados) e desmarcar tira todos', () => {
      botao('Limpar seleção').click();
      fixture.detectChanges();
      const todos =raiz.querySelector<HTMLInputElement>('thead input[type=checkbox]')!;
      todos.click();
      fixture.detectChanges();
      expect(marcados()).toEqual(['Antigo', 'Requerimento assinado', 'Ofício']);
      expect(todos.checked).toBe(true);

      todos.click();
      fixture.detectChanges();
      expect(marcados()).toEqual([]);
    });

    it('selecionar todos respeita o filtro: só os listados', () => {
      botao('Limpar seleção').click();
      digitar('oficio');
      raiz.querySelector<HTMLInputElement>('thead input[type=checkbox]')!.click();
      fixture.detectChanges();

      digitar('');
      expect(marcados()).toEqual(['Ofício']);
    });

    it('o seletor geral fica desabilitado quando nada listado é selecionável', () => {
      digitar('perdido');

      expect(raiz.querySelector<HTMLInputElement>('thead input[type=checkbox]')!.disabled).toBe(true);
    });

    it('a seleção sobrevive a filtro e ordenação', () => {
      digitar('ofício');
      digitar('');

      expect(marcados()).toEqual(['Antigo', 'Requerimento assinado', 'Ofício']);
    });
  });

  describe('busca, filtro e ordenação', () => {
    beforeEach(() => abrir());

    it('busca por texto sem considerar acento', () => {
      digitar('OFICIO');

      expect(linhas().map((l) => l.querySelector('b')!.textContent)).toEqual(['Ofício']);
      expect(texto()).toContain('Exibindo 1 de 6');
    });

    it('busca sem resultado mostra o estado vazio', () => {
      digitar('zzzz');

      expect(texto()).toContain('Nenhum documento com esses filtros');
    });

    it('filtro por origem lista só a origem e as opções vêm dos documentos', () => {
      const select = raiz.querySelector<HTMLSelectElement>('select[aria-label="Origem"]')!;
      expect(Array.from(select.options).map((o) => o.textContent)).toEqual([
        'Todas as origens',
        'Documento do processo',
        'Documento do requerimento',
        'Juntada',
      ]);

      select.value = 'JUNTADA';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(linhas().map((l) => l.querySelector('b')!.textContent)).toEqual(['Ofício']);
    });

    it('ordenar por documento alterna crescente e decrescente e marca o cabeçalho (aria-sort)', () => {
      const cabecalho = () => Array.from(raiz.querySelectorAll('th')).find((th) => th.textContent!.includes('Documento'))!;
      expect(cabecalho().getAttribute('aria-sort')).toBe('none');

      cabecalho().querySelector('button')!.click();
      fixture.detectChanges();
      expect(cabecalho().getAttribute('aria-sort')).toBe('ascending');
      expect(linhas()[0].querySelector('b')!.textContent).toBe('Antigo');

      cabecalho().querySelector('button')!.click();
      fixture.detectChanges();
      expect(cabecalho().getAttribute('aria-sort')).toBe('descending');
      expect(linhas()[0].querySelector('b')!.textContent).toBe('Requerimento assinado');
    });

    it('ordenar por origem e por data', () => {
      const ordenarPor = (nome: string) =>
        Array.from(raiz.querySelectorAll('th button')).find((b) => b.textContent!.includes(nome))!.dispatchEvent(new Event('click'));

      ordenarPor('Origem');
      fixture.detectChanges();
      expect(linhas()[0].textContent).toContain('Documento do processo');
      expect(linhas()[linhas().length - 1].textContent).toContain('Juntada');

      ordenarPor('Data');
      ordenarPor('Data');
      fixture.detectChanges();
      expect(linhas()[0].querySelector('b')!.textContent).toBe('Perdido');
    });
  });

  describe('Operador com documento sigiloso', () => {
    it('o sigiloso vem mascarado e bloqueado e nunca entra na seleção que vai para a gaveta', () => {
      abrir();
      botao('Gerar dossiê').click();
      fixture.detectChanges();

      const gaveta = document.body.querySelector('app-gaveta-dossie');
      expect(gaveta).not.toBeNull();
      const criar = TestBed.inject(DossiesApiService).criar as ReturnType<typeof vi.fn>;
      expect(criar).toHaveBeenCalledWith(101, [
        { origem: 'DOCUMENTO_PROCESSO', id: '1' },
        { origem: 'JUNTADA', id: '2' },
        { origem: 'DOCUMENTO_REQUERIMENTO', id: '6' },
      ]);
    });
  });

  describe('gerar dossiê', () => {
    beforeEach(() => abrir());

    it('desabilitado sem seleção e habilitado com pelo menos um', () => {
      expect(botao('Gerar dossiê').disabled).toBe(false);
      botao('Limpar seleção').click();
      fixture.detectChanges();
      expect(botao('Gerar dossiê').disabled).toBe(true);
    });

    it('envia à gaveta só origem e id dos selecionados (o servidor reordena cronologicamente) e a gaveta fecha', () => {
      botao('Gerar dossiê').click();
      fixture.detectChanges();

      const criar = TestBed.inject(DossiesApiService).criar as ReturnType<typeof vi.fn>;
      expect(criar).toHaveBeenCalledWith(101, [
        { origem: 'DOCUMENTO_PROCESSO', id: '1' },
        { origem: 'JUNTADA', id: '2' },
        { origem: 'DOCUMENTO_REQUERIMENTO', id: '6' },
      ]);
      expect(document.body.querySelector('app-gaveta-dossie')).not.toBeNull();

      document.body.querySelector<HTMLButtonElement>('app-gaveta-dossie .d-head .icon-btn')!.click();
      fixture.detectChanges();
      expect(document.body.querySelector('app-gaveta-dossie')).toBeNull();
    });
  });

  describe('geometrias (S4, RN10)', () => {
    const KML: GeometriaItem = {
      origem: 'DOCUMENTO_PROCESSO',
      id: '900',
      idRequerimento: 101,
      titulo: 'Polígono da área',
      nomeArquivo: 'area.kml',
      origemDescricao: 'Documento do processo',
      data: '2025-04-02T09:00:00',
      situacao: 'VIGENTE',
      sigiloso: false,
      extensao: 'kml',
      tipoArquivo: 'GEOMETRIA',
      tamanho: null,
    };

    function abrirCom(geometrias: GeometriaItem[], omitidas = 0): void {
      abrir(BASE, (r) => ({ ...r, geometrias, totais: { ...r.totais, geometrias: geometrias.length, geometriasOmitidas: omitidas } }));
    }

    const documentosDaSecao = () => raiz.querySelector('section[aria-label="Documentos"]')!;

    it('sem geometrias, a seção não aparece', () => {
      abrir();
      expect(raiz.querySelector('app-geometrias-secao')).toBeNull();
    });

    it('ficam numa seção própria, fora da tabela, dos totais e da seleção', () => {
      abrirCom([KML]);

      expect(raiz.querySelector('app-geometrias-secao')!.textContent).toContain('Polígono da área');
      expect(documentosDaSecao().textContent).not.toContain('Polígono da área');
      expect(documentosDaSecao().querySelector('.dash-sec-head p')!.textContent).toContain('6 documento(s)');
      expect(raiz.querySelector('.selbar b')!.textContent).toBe('3');
    });

    it('o POST /dossies nunca leva uma geometria', () => {
      abrirCom([KML]);
      botao('Gerar dossiê').click();
      fixture.detectChanges();

      const criar = TestBed.inject(DossiesApiService).criar as ReturnType<typeof vi.fn>;
      const enviados = criar.mock.calls[0][1] as { id: string }[];
      expect(enviados.map((r) => r.id)).not.toContain('900');
    });

    it('Operador com só sigilosas omitidas vê o aviso, sem nomes', () => {
      abrirCom([], 2);
      const secao = raiz.querySelector('app-geometrias-secao')!;
      expect(secao.textContent!.replace(/\s+/g, ' ')).toContain('2 geometria(s) sigilosa(s) não são exibidas para o seu perfil.');
      expect(secao.querySelector('table')).toBeNull();
    });
  });

  describe('visualização (modal)', () => {
    const PDF = (sobre: Partial<Extract<ConteudoRecebido, { tipo: 'blob' }>> = {}): ConteudoRecebido => ({
      tipo: 'blob',
      blob: new Blob(['%PDF-1.7'], { type: 'application/pdf' }),
      convertidoDe: null,
      nome: null,
      ...sobre,
    });
    const modal = () => document.body.querySelector('app-visualizador-documento');
    const visualizar = (titulo: string) => {
      linhaDe(titulo).querySelector<HTMLButtonElement>('button.visualizar')!.click();
      fixture.detectChanges();
    };
    const botaoDoModal = (rotulo: string) =>
      Array.from(document.body.querySelectorAll<HTMLButtonElement>('app-visualizador-documento button')).find((b) =>
        b.textContent!.includes(rotulo),
      );
    const fecharModal = () => {
      document.body.querySelector<HTMLButtonElement>('app-visualizador-documento button[aria-label="Fechar"]')!.click();
      fixture.detectChanges();
    };

    it('abre com o PDF por Blob (sem o token na URL), pede formato pdf e fecha revogando a URL de objeto', () => {
      conteudoApi.mockReturnValue(of(PDF()));
      abrir();

      visualizar('Requerimento assinado');

      expect(conteudoApi).toHaveBeenCalledWith(
        expect.objectContaining({ origem: 'DOCUMENTO_PROCESSO', id: '1', idRequerimento: 101 }),
        'pdf',
        LIMITE_DO_BLOB_MB,
      );
      expect(modal()).not.toBeNull();
      expect(modal()!.querySelector('iframe')).not.toBeNull();
      expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
      expect(modal()!.querySelector('.vis-faixa')).toBeNull();

      fecharModal();
      expect(modal()).toBeNull();
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/doc-1');
    });

    it('mostra "Convertendo para PDF…" enquanto converte um convertível e "Carregando" para os demais', () => {
      conteudoApi.mockReturnValue(NEVER);
      abrir([BASE[0], PLANILHA({ bloqueio: null, selecionavelPorPadrao: true })]);

      visualizar('Cálculo');
      expect(modal()!.textContent).toContain('Convertendo para PDF…');
      fecharModal();

      visualizar('Requerimento assinado');
      expect(modal()!.textContent).toContain('Carregando o documento…');
      expect(modal()!.textContent).not.toContain('Convertendo');
      fecharModal();
    });

    it('convertido: faixa "Versão convertida para PDF a partir de XLSX"', () => {
      conteudoApi.mockReturnValue(of(PDF({ convertidoDe: 'XLSX' })));
      abrir([BASE[0], PLANILHA({ bloqueio: null, selecionavelPorPadrao: true })]);

      visualizar('Cálculo');

      expect(modal()!.querySelector('.vis-faixa')!.textContent).toContain('Versão convertida para PDF a partir de XLSX');
      fecharModal();
    });

    it('erro pelo errorCode, sem o detail técnico, e "Tentar novamente" chama o servidor de novo', () => {
      const falha = new HttpErrorResponse({ status: 504, error: { errorCode: 'TEMPO_LIMITE_EXCEDIDO', detail: 'detalhe técnico', correlationId: 'c-1' } });
      conteudoApi.mockReturnValueOnce(throwError(() => falha)).mockReturnValueOnce(of(PDF()));
      abrir();

      visualizar('Requerimento assinado');
      expect(modal()!.querySelector('.vis-erro')!.textContent).toContain('O tempo limite foi excedido');
      expect(modal()!.textContent).not.toContain('detalhe técnico');
      expect(botaoDoModal('Baixar original')).toBeUndefined();

      botaoDoModal('Tentar novamente')!.click();
      fixture.detectChanges();

      expect(conteudoApi).toHaveBeenCalledTimes(2);
      expect(modal()!.querySelector('iframe')).not.toBeNull();
      fecharModal();
    });

    it('429: pede para tentar mais tarde; senha: oferece o original', () => {
      conteudoApi.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 429, error: { errorCode: 'MUITAS_CONVERSOES' } })));
      abrir();
      visualizar('Requerimento assinado');
      expect(modal()!.querySelector('.vis-erro')!.textContent).toContain('muitas conversões');
      expect(botaoDoModal('Baixar original')).toBeUndefined();
      fecharModal();

      conteudoApi.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 422, error: { errorCode: 'DOCUMENTO_PROTEGIDO_POR_SENHA' } })));
      visualizar('Requerimento assinado');
      expect(modal()!.querySelector('.vis-erro')!.textContent).toContain('protegido por senha');
      expect(botaoDoModal('Baixar original')).toBeDefined();
      fecharModal();
    });

    it('formato sem conversão (415): nome, tipo e "Baixar original", que pede o formato original sem limite', () => {
      conteudoApi.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 415, error: { errorCode: 'FORMATO_NAO_SUPORTADO' } })));
      abrir();
      visualizar('Requerimento assinado');

      expect(modal()!.querySelector('.vis-sem-conversao')!.textContent).toContain('não tem conversão para PDF');
      expect(modal()!.querySelector('.vis-ficha')!.textContent).toContain('requerimento.pdf');

      conteudoApi.mockReturnValueOnce(of(PDF({ nome: 'requerimento.pdf' })));
      const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
      botaoDoModal('Baixar original')!.click();
      fixture.detectChanges();

      expect(conteudoApi).toHaveBeenLastCalledWith(expect.objectContaining({ id: '1' }), 'original', null);
      expect(clique).toHaveBeenCalled();
      clique.mockRestore();
      fecharModal();
    });

    it('documento grande: sem Blob, aviso com o tamanho e só "Baixar original"', () => {
      conteudoApi.mockReturnValue(of({ tipo: 'grande', megabytes: 320 } as ConteudoRecebido));
      abrir();

      visualizar('Requerimento assinado');

      expect(modal()!.querySelector('iframe')).toBeNull();
      expect(URL.createObjectURL).not.toHaveBeenCalled();
      expect(modal()!.querySelector('.vis-grande')!.textContent).toContain('320 MB');
      expect(botaoDoModal('Baixar original')).toBeDefined();
      expect(botaoDoModal('Tentar novamente')).toBeUndefined();
      fecharModal();
    });

    it('sigiloso sem perfil: mostra o motivo e não chama o servidor', () => {
      abrir();

      visualizar('Documento sigiloso');

      expect(conteudoApi).not.toHaveBeenCalled();
      expect(modal()!.querySelector('.vis-sigiloso')!.textContent).toContain('sigiloso e não está disponível para o seu perfil');
      expect(botaoDoModal('Incluir no dossiê')).toBeUndefined();
      expect(botaoDoModal('Tirar do dossiê')).toBeUndefined();
      fecharModal();
    });

    it('seleciona e desmarca pelo modal, refletindo na tabela', () => {
      conteudoApi.mockReturnValue(of(PDF()));
      abrir();
      expect(caixa(linhaDe('Requerimento assinado')).checked).toBe(true);
      const antes = Number(selecionadosNaBarra());

      visualizar('Requerimento assinado');
      botaoDoModal('Tirar do dossiê')!.click();
      fixture.detectChanges();

      expect(caixa(linhaDe('Requerimento assinado')).checked).toBe(false);
      expect(botaoDoModal('Incluir no dossiê')).toBeDefined();
      expect(Number(selecionadosNaBarra())).toBe(antes - 1);

      botaoDoModal('Incluir no dossiê')!.click();
      fixture.detectChanges();
      expect(caixa(linhaDe('Requerimento assinado')).checked).toBe(true);
      expect(Number(selecionadosNaBarra())).toBe(antes);
      fecharModal();
    });

    it('documento que não pode ser selecionado não oferece incluir no dossiê', () => {
      conteudoApi.mockReturnValue(of(PDF()));
      abrir();

      visualizar('Cálculo');

      expect(botaoDoModal('Incluir no dossiê')).toBeUndefined();
      fecharModal();
    });

    it('Esc e clique no fundo fecham; destruir a tela não deixa modal nem URL de objeto', () => {
      conteudoApi.mockReturnValue(of(PDF()));
      abrir();

      visualizar('Requerimento assinado');
      modal()!.querySelector('section')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();
      expect(modal()).toBeNull();

      visualizar('Requerimento assinado');
      modal()!.querySelector<HTMLElement>('.vis-fundo')!.click();
      fixture.detectChanges();
      expect(modal()).toBeNull();

      visualizar('Requerimento assinado');
      fixture.destroy();
      expect(modal()).toBeNull();
      expect(URL.revokeObjectURL).toHaveBeenCalledTimes(3);
    });

    it('fechar com a resposta pendente cancela a requisição', () => {
      const pendente = new Subject<ConteudoRecebido>();
      conteudoApi.mockReturnValue(pendente);
      abrir();

      visualizar('Requerimento assinado');
      expect(pendente.observed).toBe(true);
      fecharModal();

      expect(pendente.observed).toBe(false);
    });
  });

});
