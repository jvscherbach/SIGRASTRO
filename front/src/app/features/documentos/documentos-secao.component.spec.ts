import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, of, throwError } from 'rxjs';

import { DocumentosApiService, DossiesApiService } from './documentos-api.service';
import { DocumentosSecaoComponent } from './documentos-secao.component';
import { documento } from './documentos.fixtures';
import { DocumentoItem, DocumentosResposta } from './documentos.models';

function resposta(itens: DocumentoItem[]): DocumentosResposta {
  return {
    itens,
    totais: {
      total: itens.length,
      vigentes: itens.filter((d) => d.situacao === 'VIGENTE').length,
      naoPdf: itens.filter((d) => d.bloqueio === 'NAO_PDF').length,
      tipoNaoInformado: itens.filter((d) => d.tipoArquivo === 'DESCONHECIDO').length,
      indisponiveis: itens.filter((d) => d.situacao === 'INDISPONIVEL').length,
      sigilosos: itens.filter((d) => d.sigiloso).length,
    },
  };
}

const PLANILHA = (sobre: Partial<DocumentoItem> = {}) =>
  documento({ id: '4', titulo: 'Cálculo', nomeArquivo: 'calculo.xlsx', extensao: 'xlsx', tipoArquivo: 'PLANILHA', bloqueio: 'NAO_PDF', selecionavelPorPadrao: false, data: '2025-04-04T10:00:00', ...sobre });
const INDISPONIVEL = documento({ id: '5', titulo: 'Perdido', nomeArquivo: 'perdido.pdf', situacao: 'INDISPONIVEL', bloqueio: 'INDISPONIVEL', selecionavelPorPadrao: false, data: '2025-04-05T10:00:00' });
const SUBSTITUIDO = documento({ id: '6', titulo: 'Antigo', nomeArquivo: 'antigo.pdf', origem: 'DOCUMENTO_REQUERIMENTO', origemDescricao: 'Documento do requerimento', situacao: 'SUBSTITUIDO', selecionavelPorPadrao: false, data: '2025-03-30T10:00:00' });
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
  let fixture: ComponentFixture<DocumentosSecaoComponent>;
  let raiz: HTMLElement;

  function abrir(itens: DocumentoItem[] = BASE): void {
    api.listar.mockReturnValue(of(resposta(itens)));
    TestBed.configureTestingModule({
      imports: [DocumentosSecaoComponent],
      providers: [
        { provide: DocumentosApiService, useValue: api },
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

    it('não PDF: bloqueado com "conversão disponível em breve" e aviso no topo da seção', () => {
      const linha = linhaDe('Cálculo');

      expect(caixa(linha).disabled).toBe(true);
      expect(linha.textContent).toContain('conversão disponível em breve');
      expect(raiz.querySelector('.nao-pdf')!.textContent).toContain('1 documento(s) não são PDF e entram no dossiê a partir da próxima etapa');
    });

    it('tipo não informado: etiqueta, selecionável, fora da pré-seleção, linha própria no aviso e sem contar como não PDF', () => {
      TestBed.resetTestingModule();
      const semTipo = documento({ id: '8', titulo: 'Planta', nomeArquivo: null, extensao: '', tipoArquivo: 'DESCONHECIDO', selecionavelPorPadrao: false, data: '2025-04-08T10:00:00' });
      abrir([BASE[0], semTipo]);
      const linha = linhaDe('Planta');

      expect(linha.textContent).toContain('tipo não informado');
      expect(caixa(linha).disabled).toBe(false);
      expect(caixa(linha).checked).toBe(false);
      caixa(linha).click();
      fixture.detectChanges();
      expect(caixa(linhaDe('Planta')).checked).toBe(true);
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

    it('substituído em PDF: aparece sem pré-seleção e pode ser marcado manualmente', () => {
      const linha = linhaDe('Antigo');

      expect(caixa(linha).checked).toBe(false);
      expect(caixa(linha).disabled).toBe(false);
      expect(linha.textContent).toContain('versão substituída');
      caixa(linha).click();
      fixture.detectChanges();
      expect(caixa(linhaDe('Antigo')).checked).toBe(true);
    });

    it('não há ação "Visualizar" nesta etapa', () => {
      expect(texto()).not.toMatch(/Visualizar/);
      expect(raiz.querySelector('tbody .icon-btn')).toBeNull();
    });
  });

  describe('seleção', () => {
    beforeEach(() => abrir());

    it('começa com a pré-seleção do domínio: só PDF vigente sem bloqueio', () => {
      expect(marcados()).toEqual(['Requerimento assinado', 'Ofício']);
      expect(selecionadosNaBarra()).toBe('2');
    });

    it('seleção individual atualiza o contador', () => {
      caixa(linhaDe('Antigo')).click();
      fixture.detectChanges();

      expect(selecionadosNaBarra()).toBe('3');
      caixa(linhaDe('Ofício')).click();
      fixture.detectChanges();
      expect(selecionadosNaBarra()).toBe('2');
    });

    it('"Limpar seleção" zera e "Seleção padrão" restaura a pré-seleção', () => {
      botao('Limpar seleção').click();
      fixture.detectChanges();
      expect(marcados()).toEqual([]);
      expect(botao('Gerar dossiê').disabled).toBe(true);

      caixa(linhaDe('Antigo')).click();
      fixture.detectChanges();
      botao('Seleção padrão').click();
      fixture.detectChanges();
      expect(marcados()).toEqual(['Requerimento assinado', 'Ofício']);
    });

    it('selecionar todos marca só o que pode ser selecionado (não os bloqueados) e desmarcar tira todos', () => {
      const todos = raiz.querySelector<HTMLInputElement>('thead input[type=checkbox]')!;
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

      expect(marcados()).toEqual(['Requerimento assinado', 'Ofício']);
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
      const todos = raiz.querySelector<HTMLInputElement>('thead input[type=checkbox]')!;
      todos.click();
      fixture.detectChanges();
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
      ]);
      expect(document.body.querySelector('app-gaveta-dossie')).not.toBeNull();

      document.body.querySelector<HTMLButtonElement>('app-gaveta-dossie .d-head .icon-btn')!.click();
      fixture.detectChanges();
      expect(document.body.querySelector('app-gaveta-dossie')).toBeNull();
    });
  });
});
