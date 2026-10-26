import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { DossiesApiService } from './documentos-api.service';
import { AndamentoDossie, ReferenciaDocumento } from './documentos.models';
import { GavetaDossieComponent, INTERVALO_INICIAL_MS, INTERVALO_MAXIMO_MS } from './gaveta-dossie.component';

const REFS: ReferenciaDocumento[] = [
  { origem: 'DOCUMENTO_PROCESSO', id: '804328' },
  { origem: 'JUNTADA', id: '900118' },
  { origem: 'IMOVEL', id: '31' },
];

function andamento(sobre: Partial<AndamentoDossie> = {}): AndamentoDossie {
  return {
    id: 'pedido-1',
    status: 'OBTENDO',
    total: 3,
    processados: 1,
    erro: null,
    falhas: [],
    paginas: null,
    expiraEm: '2026-10-21T11:30:00',
    itens: [
      { origem: 'DOCUMENTO_PROCESSO', id: '804328', titulo: 'Requerimento assinado', situacao: 'OK' },
      { origem: 'JUNTADA', id: '900118', titulo: 'Ofício juntado', situacao: 'OBTENDO' },
      { origem: 'IMOVEL', id: '31', titulo: 'Matrícula', situacao: 'AGUARDANDO' },
    ],
    ...sobre,
  };
}

const CONCLUIDO = andamento({
  status: 'CONCLUIDO',
  processados: 3,
  paginas: 14,
  itens: andamento().itens.map((i) => ({ ...i, situacao: 'OK' as const })),
});

const FALHA = andamento({
  status: 'PENDENTE_DECISAO',
  processados: 2,
  falhas: [{ origem: 'JUNTADA', id: '900118', titulo: 'Ofício juntado', motivo: 'tempo limite excedido ao obter o arquivo' }],
  itens: [
    { origem: 'DOCUMENTO_PROCESSO', id: '804328', titulo: 'Requerimento assinado', situacao: 'OK' },
    { origem: 'JUNTADA', id: '900118', titulo: 'Ofício juntado', situacao: 'FALHA', motivo: 'tempo limite excedido ao obter o arquivo' },
    { origem: 'IMOVEL', id: '31', titulo: 'Matrícula', situacao: 'OK' },
  ],
});

function pdf(cabecalho?: string): HttpResponse<Blob> {
  return new HttpResponse({
    body: new Blob(['%PDF-1.7'], { type: 'application/pdf' }),
    status: 200,
    headers: new HttpHeaders(cabecalho ? { 'Content-Disposition': cabecalho } : {}),
  });
}

describe('GavetaDossieComponent', () => {
  let api: {
    criar: ReturnType<typeof vi.fn>;
    andamento: ReturnType<typeof vi.fn>;
    arquivo: ReturnType<typeof vi.fn>;
    cancelar: ReturnType<typeof vi.fn>;
    continuar: ReturnType<typeof vi.fn>;
  };
  let fixture: ComponentFixture<GavetaDossieComponent>;
  let raiz: HTMLElement;
  let removidos: ReferenciaDocumento[];
  let fechou: number;

  function abrir(refs: ReferenciaDocumento[] = REFS): void {
    TestBed.configureTestingModule({ imports: [GavetaDossieComponent], providers: [{ provide: DossiesApiService, useValue: api }] });
    fixture = TestBed.createComponent(GavetaDossieComponent);
    fixture.componentRef.setInput('idRequerimento', 101);
    fixture.componentRef.setInput('referencias', refs);
    fixture.componentInstance.documentoRemovido.subscribe((r) => removidos.push(r));
    fixture.componentInstance.fechar.subscribe(() => fechou++);
    fixture.detectChanges();
    raiz = fixture.nativeElement as HTMLElement;
  }

  const texto = () => raiz.textContent!.replace(/\s+/g, ' ');
  const botao = (rotulo: string) =>
    Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes(rotulo));
  const passos = () =>
    Array.from(raiz.querySelectorAll('.step')).map((s) => ({
      titulo: s.querySelector('b')!.textContent,
      situacao: s.getAttribute('data-situacao'),
    }));
  const avancar = async (ms: number) => {
    await vi.advanceTimersByTimeAsync(ms);
    fixture.detectChanges();
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-21T15:00:00Z')); // 11:00 em Cuiabá
    removidos = [];
    fechou = 0;
    api = {
      criar: vi.fn(() => of({ id: 'pedido-1' })),
      andamento: vi.fn(() => of(andamento())),
      arquivo: vi.fn(),
      cancelar: vi.fn(() => of(undefined)),
      continuar: vi.fn(() => of(undefined)),
    };
    URL.createObjectURL = vi.fn(() => 'blob:http://localhost/previa-1');
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  describe('criação e andamento', () => {
    it('cria o pedido com a seleção recebida e já consulta o andamento', () => {
      abrir();

      expect(api.criar).toHaveBeenCalledWith(101, REFS);
      expect(api.andamento).toHaveBeenCalledWith('pedido-1');
    });

    it('avisa que o dossiê segue sempre a ordem cronológica (a ordem da tabela não muda o PDF)', () => {
      abrir();

      expect(texto()).toContain('O dossiê segue sempre a ordem cronológica.');
    });

    it('enquanto o pedido é enviado mostra "Enviando pedido…" e a lista da seleção', () => {
      api.criar.mockReturnValue(new Subject());
      abrir();

      expect(texto()).toContain('Enviando pedido…');
      expect(passos().length).toBe(3);
    });

    it('processando: progresso processados/total, percentual e a situação de cada documento', () => {
      abrir();

      expect(texto()).toContain('Gerando dossiê');
      expect(texto()).toContain('Processando · 1/3');
      expect(texto()).toContain('33%');
      expect(passos()).toEqual([
        { titulo: 'Requerimento assinado', situacao: 'OK' },
        { titulo: 'Ofício juntado', situacao: 'OBTENDO' },
        { titulo: 'Matrícula', situacao: 'AGUARDANDO' },
      ]);
      expect(texto()).toContain('Obtendo arquivo…');
      expect(botao('Cancelar geração')).toBeTruthy();
      expect(botao('Pré-visualizar')).toBeUndefined();
    });

    it('pendente aparece como "Na fila"', () => {
      api.andamento.mockReturnValue(of(andamento({ status: 'AGUARDANDO', processados: 0 })));
      abrir();

      expect(texto()).toContain('Na fila');
    });
  });

  describe('polling', () => {
    it('intervalo crescente: 1 s, 1,5 s, 2,25 s, 3,4 s e no máximo 5 s', async () => {
      abrir();
      expect(api.andamento).toHaveBeenCalledTimes(1);

      const esperados = [INTERVALO_INICIAL_MS, 1500, 2250, 3375, INTERVALO_MAXIMO_MS, INTERVALO_MAXIMO_MS];
      let chamadas = 1;
      for (const espera of esperados) {
        await avancar(espera - 1);
        expect(api.andamento, `antes de ${espera} ms`).toHaveBeenCalledTimes(chamadas);
        await avancar(1);
        chamadas += 1;
        expect(api.andamento, `em ${espera} ms`).toHaveBeenCalledTimes(chamadas);
      }
    });

    it('para ao chegar a um estado que depende do usuário (concluído, aguardando decisão, cancelado, expirado)', async () => {
      for (const status of ['CONCLUIDO', 'PENDENTE_DECISAO', 'CANCELADO', 'EXPIRADO'] as const) {
        TestBed.resetTestingModule();
        api.andamento.mockClear();
        api.andamento.mockReturnValue(of(andamento({ status, falhas: status === 'PENDENTE_DECISAO' ? FALHA.falhas : [] })));
        abrir();

        await avancar(60_000);

        expect(api.andamento, status).toHaveBeenCalledTimes(1);
      }
    });

    it('continua enquanto não é final e para quando o andamento chega ao fim', async () => {
      const respostas = [andamento(), andamento({ processados: 2 }), CONCLUIDO];
      api.andamento.mockImplementation(() => of(respostas.shift() ?? CONCLUIDO));
      abrir();

      await avancar(60_000);

      expect(api.andamento).toHaveBeenCalledTimes(3);
      expect(texto()).toContain('Dossiê pronto');
    });

    it('fechar a gaveta (destruir o componente) cancela o timer e a consulta em andamento', async () => {
      const pendente = new Subject<AndamentoDossie>();
      api.andamento.mockReturnValueOnce(of(andamento())).mockReturnValue(pendente);
      abrir();
      await avancar(INTERVALO_INICIAL_MS); // a segunda consulta fica pendurada em "pendente"
      expect(api.andamento).toHaveBeenCalledTimes(2);
      expect(pendente.observed).toBe(true);

      fixture.destroy();

      expect(pendente.observed).toBe(false);
      await vi.advanceTimersByTimeAsync(120_000);
      expect(api.andamento).toHaveBeenCalledTimes(2);
      expect(vi.getTimerCount()).toBe(0);
    });

    it('destruir enquanto espera o próximo ciclo não deixa timer algum', async () => {
      abrir();
      expect(vi.getTimerCount()).toBe(1);

      fixture.destroy();

      expect(vi.getTimerCount()).toBe(0);
    });

    it('erro ao consultar para o polling e oferece "Atualizar"', async () => {
      api.andamento.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: { errorCode: 'ERRO_INTERNO' } })));
      abrir();

      await avancar(60_000);

      expect(api.andamento).toHaveBeenCalledTimes(1);
      expect(texto()).toContain('Ocorreu um erro inesperado.');
      expect(botao('Atualizar')).toBeTruthy();
      expect(botao('Tentar novamente')).toBeUndefined();

      api.andamento.mockReturnValue(of(CONCLUIDO));
      botao('Atualizar')!.click();
      fixture.detectChanges();
      expect(texto()).toContain('Dossiê pronto');
    });
  });

  describe('concluído', () => {
    beforeEach(() => api.andamento.mockReturnValue(of(CONCLUIDO)));

    it('mostra "Dossiê pronto", páginas, validade e os botões de pré-visualizar e baixar', () => {
      abrir();

      expect(texto()).toContain('Dossiê pronto');
      expect(texto()).toContain('Concluído · 3/3');
      expect(texto()).toContain('100%');
      expect(texto()).toContain('21/10/2026 11:30');
      expect(texto()).toContain('restam 30 min');
      expect(texto()).toContain('14 páginas');
      expect(botao('Pré-visualizar')!.disabled).toBe(false);
      expect(botao('Baixar PDF')!.disabled).toBe(false);
    });

    it('o tempo restante cai com o relógio e, no fim, a gaveta marca o dossiê como expirado', async () => {
      abrir();

      await avancar(10 * 60_000);
      expect(texto()).toContain('restam 20 min');

      await avancar(21 * 60_000);
      expect(texto()).toContain('Dossiê expirado');
      expect(texto()).toContain('O dossiê expirou e o arquivo foi descartado');
      expect(botao('Gerar novamente')).toBeTruthy();
      expect(botao('Baixar PDF')).toBeUndefined();
    });

    it('pré-visualizar busca o PDF inline por Blob e abre numa moldura com URL de objeto, revogada ao fechar', () => {
      api.arquivo.mockReturnValue(of(pdf('inline; filename="dossie-processo-2825-2025.pdf"')));
      abrir();

      botao('Pré-visualizar')!.click();
      fixture.detectChanges();

      expect(api.arquivo).toHaveBeenCalledWith('pedido-1', 'inline');
      expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
      expect((URL.createObjectURL as ReturnType<typeof vi.fn>).mock.calls[0][0]).toBeInstanceOf(Blob);
      expect(raiz.querySelector('.previa iframe')).not.toBeNull();
      expect(raiz.querySelector('.previa iframe')!.getAttribute('src')).toBe('blob:http://localhost/previa-1');

      raiz.querySelector<HTMLButtonElement>('.previa .icon-btn')!.click();
      fixture.detectChanges();
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/previa-1');
      expect(raiz.querySelector('.previa')).toBeNull();
    });

    it('destruir a gaveta com a pré-visualização aberta revoga a URL de objeto', () => {
      api.arquivo.mockReturnValue(of(pdf()));
      abrir();
      botao('Pré-visualizar')!.click();
      fixture.detectChanges();

      fixture.destroy();

      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/previa-1');
    });

    it('baixar usa Blob e o nome do Content-Disposition; depois o arquivo é tratado como descartado', () => {
      api.arquivo.mockReturnValue(of(pdf("attachment; filename*=UTF-8''dossie-processo-2825-2025.pdf")));
      const baixados: { nome: string; href: string }[] = [];
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
        baixados.push({ nome: this.download, href: this.href });
      });
      abrir();

      botao('Baixar PDF')!.click();
      fixture.detectChanges();

      expect(api.arquivo).toHaveBeenCalledWith('pedido-1', 'attachment');
      expect(baixados).toEqual([{ nome: 'dossie-processo-2825-2025.pdf', href: 'blob:http://localhost/previa-1' }]);
      expect(texto()).toContain('Arquivo baixado. Ele foi descartado do servidor');
      expect(botao('Baixar PDF')).toBeUndefined();
      expect(botao('Gerar novamente')).toBeTruthy();
    });

    it('sem Content-Disposition o nome padrão é dossie.pdf', () => {
      api.arquivo.mockReturnValue(of(pdf()));
      const nomes: string[] = [];
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
        nomes.push(this.download);
      });
      abrir();

      botao('Baixar PDF')!.click();

      expect(nomes).toEqual(['dossie.pdf']);
    });

    it('o token nunca aparece em URL: a busca do PDF só leva id e disposição', () => {
      api.arquivo.mockReturnValue(of(pdf()));
      abrir();

      botao('Pré-visualizar')!.click();

      expect(api.arquivo.mock.calls).toEqual([['pedido-1', 'inline']]);
    });

    it('arquivo que já expirou no servidor (410) vira "expirado" com a mensagem da API', () => {
      api.arquivo.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 410, error: { errorCode: 'DOSSIE_EXPIRADO' } })));
      abrir();

      botao('Pré-visualizar')!.click();
      fixture.detectChanges();

      expect(texto()).toContain('O dossiê expirou. Gere-o novamente.');
      expect(texto()).toContain('Dossiê expirado');
      expect(botao('Gerar novamente')).toBeTruthy();
    });
  });

  describe('aguardando decisão (RN09, S2.2)', () => {
    beforeEach(() => api.andamento.mockReturnValue(of(FALHA)));

    it('informa o documento e o motivo, mantém os obtidos e não entrega nada', () => {
      abrir();

      expect(texto()).toContain('Documentos com problema');
      expect(texto()).toContain('Ofício juntado não pôde ser incluído: tempo limite excedido ao obter o arquivo.');
      expect(texto()).toContain('não serão baixados de novo');
      expect(texto()).toContain('Aguardando decisão · 2/3');
      expect(passos().map((p) => p.situacao)).toEqual(['OK', 'FALHA', 'OK']);
      expect(botao('Pré-visualizar')).toBeUndefined();
      expect(botao('Baixar PDF')).toBeUndefined();
    });

    it('"Tentar novamente" continua o mesmo pedido (sem novo POST) e volta a acompanhar', () => {
      abrir();
      api.criar.mockClear();
      api.andamento.mockClear();
      api.andamento.mockReturnValue(of(CONCLUIDO));

      botao('Tentar novamente')!.click();
      fixture.detectChanges();

      expect(api.continuar).toHaveBeenCalledWith('pedido-1', 'TENTAR_NOVAMENTE');
      expect(api.criar).not.toHaveBeenCalled();
      expect(api.andamento).toHaveBeenCalledTimes(1);
      expect(texto()).toContain('Dossiê pronto');
      expect(removidos).toEqual([]);
    });

    it('"Remover documento e gerar" decide REMOVER_E_CONTINUAR e avisa a tela do que saiu', () => {
      abrir();
      api.criar.mockClear();

      botao('Remover documento e gerar')!.click();

      expect(api.continuar).toHaveBeenCalledWith('pedido-1', 'REMOVER_E_CONTINUAR');
      expect(api.criar).not.toHaveBeenCalled();
      expect(removidos).toEqual([{ origem: 'JUNTADA', id: '900118' }]);
    });

    it('o progresso não volta a zero depois da decisão', () => {
      abrir();
      api.andamento.mockReturnValue(of(andamento({ status: 'OBTENDO', processados: 2 })));

      botao('Tentar novamente')!.click();
      fixture.detectChanges();

      expect(texto()).toContain('2/3');
    });

    it('decisão recusada pelo servidor (409) mostra a mensagem e mantém os botões', () => {
      api.continuar.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error: { errorCode: 'DOSSIE_SEM_DECISAO_PENDENTE' } })),
      );
      abrir();

      botao('Tentar novamente')!.click();
      fixture.detectChanges();

      expect(raiz.querySelector('.gaveta-erro')).not.toBeNull();
      expect(botao('Tentar novamente')).toBeTruthy();
    });

    it('sem nenhum documento obtido não há o que remover', () => {
      api.andamento.mockReturnValue(
        of({ ...FALHA, processados: 0, itens: FALHA.itens.map((i) => ({ ...i, situacao: i.situacao === 'OK' ? 'AGUARDANDO' as const : i.situacao })) }),
      );
      abrir();

      expect(botao('Remover documento e gerar')).toBeUndefined();
      expect(botao('Tentar novamente')).toBeTruthy();
    });
  });

  describe('token da API de arquivos recusado', () => {
    const recusado = andamento({
      status: 'PENDENTE_DECISAO',
      erro: {
        origem: 'DOCUMENTO_PROCESSO',
        id: '804328',
        titulo: 'Requerimento assinado',
        motivo: 'Token da API de arquivos expirado ou recusado: capture um novo token.',
        codigo: 'CREDENCIAL_ARQUIVOS_RECUSADA',
      },
      falhas: [
        {
          origem: 'DOCUMENTO_PROCESSO',
          id: '804328',
          titulo: 'Requerimento assinado',
          motivo: 'Token da API de arquivos expirado ou recusado: capture um novo token.',
        },
      ],
      processados: 0,
      itens: [
        { origem: 'DOCUMENTO_PROCESSO', id: '804328', titulo: 'Requerimento assinado', situacao: 'FALHA' },
        { origem: 'JUNTADA', id: '900118', titulo: 'Ofício juntado', situacao: 'AGUARDANDO' },
        { origem: 'IMOVEL', id: '31', titulo: 'Matrícula', situacao: 'AGUARDANDO' },
      ],
    });

    it('orienta a capturar um novo token, sem citar valor e sem oferecer a remoção', () => {
      api.andamento.mockReturnValue(of(recusado));
      abrir();

      expect(texto()).toContain('Token da API de arquivos expirado ou recusado.');
      expect(texto()).toContain('Capture um novo token e tente novamente.');
      expect(botao('Remover documento e gerar')).toBeUndefined();
      expect(botao('Tentar novamente')).toBeTruthy();
      expect(raiz.innerHTML).not.toMatch(/Bearer|eyJ/);
    });
  });

  describe('cancelado e expirado', () => {
    it('cancelado', () => {
      api.andamento.mockReturnValue(of(andamento({ status: 'CANCELADO' })));
      abrir();

      expect(texto()).toContain('Geração cancelada');
      expect(texto()).toContain('Nenhum arquivo foi gerado.');
      expect(botao('Gerar novamente')).toBeTruthy();
    });

    it('expirado pelo servidor', () => {
      api.andamento.mockReturnValue(of(andamento({ status: 'EXPIRADO' })));
      abrir();

      expect(texto()).toContain('Dossiê expirado');
      expect(botao('Gerar novamente')).toBeTruthy();
      expect(botao('Baixar PDF')).toBeUndefined();
    });

    it('"Cancelar geração" chama DELETE, para o polling e reconsulta o estado', async () => {
      const respostas = [andamento(), andamento({ status: 'CANCELADO' })];
      api.andamento.mockImplementation(() => of(respostas.shift() ?? andamento({ status: 'CANCELADO' })));
      abrir();

      botao('Cancelar geração')!.click();
      fixture.detectChanges();

      expect(api.cancelar).toHaveBeenCalledWith('pedido-1');
      expect(texto()).toContain('Geração cancelada');
      await avancar(60_000);
      expect(api.andamento).toHaveBeenCalledTimes(2);
    });
  });

  describe('erro ao criar o pedido', () => {
    function falhaAoCriar(status: number, corpo: object): void {
      api.criar.mockReturnValue(throwError(() => new HttpErrorResponse({ status, error: corpo })));
      abrir();
    }

    it('documento sigiloso (403)', () => {
      falhaAoCriar(403, { errorCode: 'DOCUMENTO_SIGILOSO' });

      expect(texto()).toContain('Este documento é sigiloso e não está disponível para o seu perfil.');
      expect(api.andamento).not.toHaveBeenCalled();
      expect(botao('Tentar novamente')).toBeTruthy();
    });

    it('seleção inválida (400) mostra o texto escrito para o usuário pela API', () => {
      falhaAoCriar(400, { errorCode: 'PARAMETRO_INVALIDO', detail: 'O documento JUNTADA/900118 está indisponível e não pode entrar no dossiê.' });

      expect(texto()).toContain('O documento JUNTADA/900118 está indisponível e não pode entrar no dossiê.');
    });

    it('SIGA fora do ar (503) e erro de rede', () => {
      falhaAoCriar(503, { errorCode: 'SIGA_INDISPONIVEL' });
      expect(texto()).toContain('O banco do SIGA está indisponível no momento.');

      TestBed.resetTestingModule();
      falhaAoCriar(0, {});
      expect(texto()).toContain('Não foi possível conectar ao servidor.');
    });

    it('"Tentar novamente" refaz o POST', () => {
      falhaAoCriar(503, { errorCode: 'SIGA_INDISPONIVEL' });
      api.criar.mockClear();
      api.criar.mockReturnValue(of({ id: 'pedido-2' }));

      botao('Tentar novamente')!.click();
      fixture.detectChanges();

      expect(api.criar).toHaveBeenCalledTimes(1);
      expect(api.andamento).toHaveBeenCalledWith('pedido-2');
    });
  });

  describe('fechar', () => {
    it('o botão de fechar avisa a tela e, em andamento, informa que a geração continua', () => {
      abrir();
      const fechar = raiz.querySelector<HTMLButtonElement>('.d-head .icon-btn')!;

      expect(fechar.getAttribute('title')).toContain('a geração continua');
      fechar.click();
      expect(fechou).toBe(1);
    });

    it('clicar no fundo escuro fecha; clicar dentro da gaveta não', () => {
      abrir();

      raiz.querySelector<HTMLElement>('.drawer')!.click();
      expect(fechou).toBe(0);
      raiz.querySelector<HTMLElement>('.scrim')!.click();
      expect(fechou).toBe(1);
    });
  });
});

