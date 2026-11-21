import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, of, throwError } from 'rxjs';

import { DetalheApiService } from './detalhe-api.service';
import {
  AtoDaPassagem,
  EtapaDaPassagem,
  PassagemDaTramitacao,
  ResponsavelDaPassagem,
  TramitacaoDoRequerimento,
} from './tramitacao.models';
import { TramitacaoComponent } from './tramitacao.component';

/** Dia de março de 2026, 09:00 (hora de Cuiabá, sem fuso, como a API manda). */
const dia = (n: number, hora = 9): string => `2026-03-${String(n).padStart(2, '0')}T${String(hora).padStart(2, '0')}:00:00`;

const CADASTRO: EtapaDaPassagem = { id: 'cadastro_do_requerimento', rotulo: 'Cadastro do requerimento', tipo: 'TAREFA_HUMANA' };
const CONFERENCIA: EtapaDaPassagem = { id: 'conferencia_dos_documentos', rotulo: 'Conferência dos documentos', tipo: 'TAREFA_HUMANA' };
const TAXA: EtapaDaPassagem = { id: 'emissao_da_taxa', rotulo: 'Emissão da taxa', tipo: 'AUTOMATICA' };
const PAGAMENTO: EtapaDaPassagem = { id: 'aguardando_pagamento', rotulo: 'Aguardando pagamento', tipo: 'ESPERA' };
const ANALISE: EtapaDaPassagem = { id: 'analise_do_processo', rotulo: 'Análise do processo', tipo: 'TAREFA_HUMANA' };
const REVISAO: EtapaDaPassagem = { id: 'revisao_do_parecer_tecnico', rotulo: 'Revisão do parecer técnico', tipo: 'TAREFA_HUMANA' };
const VALIDACAO: EtapaDaPassagem = { id: 'validacao_do_parecer_tecnico', rotulo: 'Validação do parecer técnico', tipo: 'TAREFA_HUMANA' };
const EMISSAO: EtapaDaPassagem = { id: 'emissao_do_ato', rotulo: 'Emissão do ato', tipo: 'EMISSAO' };
const CORRECAO: EtapaDaPassagem = { id: 'correcao_do_requerimento', rotulo: 'Correção do requerimento', tipo: 'TAREFA_HUMANA' };
const VERIFICACAO: EtapaDaPassagem = {
  id: 'verificacao_da_revisao_do_processo',
  rotulo: 'Verificação da revisão do processo',
  tipo: 'TAREFA_HUMANA',
};

function pessoa(papel: string, nome: string | null, estado: ResponsavelDaPassagem['estado'] = 'CONCLUIU'): ResponsavelDaPassagem {
  return { tipo: nome ? 'SERVIDOR' : 'NAO_IDENTIFICADO', papel, nome, inicio: dia(3), fim: estado === 'PENDENTE' ? null : dia(4), estado };
}

function passagem(etapa: EtapaDaPassagem, entrada: number, saida: number | null, sobre: Partial<PassagemDaTramitacao> = {}): PassagemDaTramitacao {
  return {
    etapa,
    ciclo: 1,
    ramo: 'PRINCIPAL',
    entrada: dia(entrada),
    saida: saida === null ? null : dia(saida),
    duracaoSegundos: saida === null ? null : (saida - entrada) * 86400,
    atual: saida === null,
    emParalelo: false,
    processoConcluido: false,
    motivoDaVolta: null,
    responsaveis: [],
    participantes: [],
    atos: [],
    ...sobre,
  };
}

function tramitacao(passagens: PassagemDaTramitacao[], sobre: Partial<TramitacaoDoRequerimento> = {}): TramitacaoDoRequerimento {
  return {
    idRequerimento: 8001,
    numeroRequerimento: '8001/2026',
    numeroProcesso: '8001/2026',
    situacaoLicenciamento: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
    modelo: 'Outorga de recursos hídricos',
    estado: 'ATIVO',
    aproximado: false,
    truncado: false,
    passagens,
    complementacoes: [],
    final: null,
    avisos: [],
    planoB: null,
    ...sobre,
  };
}

/** HC01: caminho feliz taxável (a emissão da taxa é automática), com a verificação da revisão como etapa atual. */
const HC01 = tramitacao(
  [
    passagem(CADASTRO, 2, 3, { responsaveis: [pessoa('Interessado (cadastrante)', null)] }),
    passagem(CONFERENCIA, 3, 4, { responsaveis: [pessoa('Conferidor', 'Servidor Exemplo 02')] }),
    passagem(TAXA, 4, 4),
    passagem(PAGAMENTO, 4, 7),
    passagem(ANALISE, 7, 9, {
      participantes: [pessoa('Analista', 'Servidor Exemplo 05'), pessoa('Analista', 'Servidor Exemplo 06'), pessoa('Analista', null)],
      duracaoSegundos: 2 * 86400 + 3 * 3600,
    }),
    passagem(VERIFICACAO, 9, null, { processoConcluido: true }),
  ],
  { final: { rotulo: 'Concluído', tipo: 'CONCLUIDO_NA_VERIFICACAO', encerradoEm: dia(9) } },
);

describe('TramitacaoComponent', () => {
  let api: { tramitacao: ReturnType<typeof vi.fn> };

  function criar(): { fixture: ComponentFixture<TramitacaoComponent>; raiz: HTMLElement } {
    TestBed.configureTestingModule({
      imports: [TramitacaoComponent],
      providers: [{ provide: DetalheApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(TramitacaoComponent);
    fixture.componentRef.setInput('idRequerimento', 8001);
    fixture.detectChanges();
    return { fixture, raiz: fixture.nativeElement as HTMLElement };
  }

  const texto = (raiz: HTMLElement) => raiz.textContent!.replace(/\s+/g, ' ');
  const itens = (raiz: HTMLElement) => Array.from(raiz.querySelectorAll('.tram-lista > li'));
  const rotulos = (raiz: HTMLElement) => itens(raiz).map((li) => li.querySelector('.tram-titulo b')!.textContent!.trim());

  function semIdTecnicoNemCpf(raiz: HTMLElement): void {
    const t = raiz.textContent!;
    expect(t).not.toMatch(/task|gtw|gwt|Event_|Activity_|procSurh|procOutorga/);
    expect(t).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}/);
    expect(t).not.toMatch(/(?<!\d)\d{11}(?!\d)/);
  }

  beforeEach(() => {
    api = { tramitacao: vi.fn() };
  });

  describe('caminho feliz (HC01)', () => {
    it('lista as etapas na ordem, com entrada, saída, duração e a etapa atual', () => {
      api.tramitacao.mockReturnValue(of(HC01));
      const { raiz } = criar();

      // A emissão da taxa é automática: fica fora com o filtro padrão.
      expect(rotulos(raiz)).toEqual([
        'Cadastro do requerimento',
        'Conferência dos documentos',
        'Aguardando pagamento',
        'Análise do processo',
        'Verificação da revisão do processo',
      ]);
      const analise = itens(raiz)[3];
      expect(analise.textContent).toContain('Duração 2 dias e 3 h');
      expect(analise.textContent).toContain('Entrada');
      expect(analise.textContent).toContain('Saída');
      const atual = itens(raiz)[4];
      expect(atual.classList.contains('atual')).toBe(true);
      expect(atual.textContent).toContain('Etapa atual');
      expect(atual.textContent).toContain('Em andamento');
      expect(atual.textContent).toContain('Processo concluído');
      expect(api.tramitacao).toHaveBeenCalledWith(8001);
    });

    it('com quem esteve: nome e papel; participantes da multi-instância com concluiu/pendente', () => {
      api.tramitacao.mockReturnValue(of(HC01));
      const { raiz } = criar();

      expect(itens(raiz)[1].textContent).toContain('Servidor Exemplo 02 · Conferidor');
      const participantes = Array.from(itens(raiz)[3].querySelectorAll('.tram-pessoas li')).map((l) => l.textContent!.replace(/\s+/g, ' ').trim());
      expect(participantes).toEqual([
        expect.stringContaining('Servidor Exemplo 05 · Analista'),
        expect.stringContaining('Servidor Exemplo 06 · Analista'),
        expect.stringContaining('Analista'),
      ]);
      expect(itens(raiz)[3].querySelectorAll('.tram-pessoas .tag')[0].textContent).toBe('Concluiu');
    });

    it('o final mostra o rótulo e a data; sem evento de fim não há selo "Final"', () => {
      api.tramitacao.mockReturnValue(of(HC01));
      const { raiz } = criar();

      expect(texto(raiz.querySelector('.tram-final')!)).toContain('Concluído em 09/03/2026');
      expect(raiz.querySelector('.tram-final .tag')).toBeNull();
    });

    it('final com evento de fim ganha o selo "Final"', () => {
      api.tramitacao.mockReturnValue(
        of(tramitacao([passagem(CADASTRO, 2, 3)], { estado: 'ENCERRADO', final: { rotulo: 'Cancelado pelo cadastrante', tipo: 'FINAL', encerradoEm: dia(12) } })),
      );
      const { raiz } = criar();

      expect(texto(raiz.querySelector('.tram-final')!)).toContain('Cancelado pelo cadastrante em 12/03/2026');
      expect(raiz.querySelector('.tram-final .tag')!.textContent).toBe('Final');
    });

    it('nenhum id técnico nem CPF no DOM', () => {
      api.tramitacao.mockReturnValue(of(HC01));

      semIdTecnicoNemCpf(criar().raiz);
    });
  });

  describe('filtro "ocultar etapas automáticas"', () => {
    it('vem ligado e a lista completa fica a um clique', () => {
      api.tramitacao.mockReturnValue(of(HC01));
      const { fixture, raiz } = criar();
      const filtro = raiz.querySelector<HTMLInputElement>('.tram-filtro input')!;

      expect(filtro.checked).toBe(true);
      expect(texto(raiz)).toContain('1 etapa automática oculta');

      filtro.click();
      fixture.detectChanges();

      expect(rotulos(raiz)).toContain('Emissão da taxa');
      expect(rotulos(raiz)).toHaveLength(6);
      expect(texto(raiz)).not.toContain('etapa automática oculta');
    });

    it('nunca esconde a etapa atual, mesmo automática', () => {
      api.tramitacao.mockReturnValue(of(tramitacao([passagem(CADASTRO, 2, 3), passagem(TAXA, 3, null)])));
      const { raiz } = criar();

      expect(rotulos(raiz)).toEqual(['Cadastro do requerimento', 'Emissão da taxa']);
    });
  });

  describe('voltas e ramo da correção', () => {
    it('HC03: conferência reprova e volta ao cadastro, na ordem real', () => {
      api.tramitacao.mockReturnValue(
        of(
          tramitacao([
            passagem(CADASTRO, 2, 3),
            passagem(CONFERENCIA, 3, 4),
            passagem(CADASTRO, 4, 5, { ciclo: 2, motivoDaVolta: { codigo: 'REPROVADO_NA_CONFERENCIA', texto: 'devolvido na conferência dos documentos' } }),
            passagem(CONFERENCIA, 5, null, { ciclo: 2 }),
          ]),
        ),
      );
      const { raiz } = criar();

      expect(rotulos(raiz)).toEqual(['Cadastro do requerimento', 'Conferência dos documentos', 'Cadastro do requerimento', 'Conferência dos documentos']);
      expect(itens(raiz)[2].querySelector('.tram-volta')!.textContent).toBe('2ª passagem: devolvido na conferência dos documentos');
      // 2ª passagem sem motivo determinável: só o número, nunca um motivo inventado
      expect(itens(raiz)[3].querySelector('.tram-volta')!.textContent).toBe('2ª passagem');
      expect(itens(raiz)[0].querySelector('.tram-volta')).toBeNull();
    });

    it('HC04: devolvida na validação (volta à análise e depois à revisão)', () => {
      api.tramitacao.mockReturnValue(
        of(
          tramitacao([
            passagem(ANALISE, 2, 3),
            passagem(REVISAO, 3, 4),
            passagem(VALIDACAO, 4, 5),
            passagem(ANALISE, 5, 6, { ciclo: 2, motivoDaVolta: { codigo: 'DEVOLVIDO_NA_VALIDACAO_PARA_ANALISE', texto: 'devolvido na validação do parecer' } }),
            passagem(REVISAO, 6, 7, { ciclo: 2 }),
            passagem(VALIDACAO, 7, 8, { ciclo: 2 }),
            passagem(REVISAO, 8, null, { ciclo: 3, motivoDaVolta: { codigo: 'DEVOLVIDO_NA_VALIDACAO_PARA_REVISAO', texto: 'devolvido na validação do parecer' } }),
          ]),
        ),
      );
      const { raiz } = criar();

      expect(itens(raiz)).toHaveLength(7);
      expect(itens(raiz)[3].querySelector('.tram-volta')!.textContent).toBe('2ª passagem: devolvido na validação do parecer');
      expect(itens(raiz)[6].querySelector('.tram-volta')!.textContent).toBe('3ª passagem: devolvido na validação do parecer');
    });

    it('HC07: o ramo da correção aparece recuado, com marcador em texto', () => {
      api.tramitacao.mockReturnValue(
        of(
          tramitacao([
            passagem(ANALISE, 2, 5),
            passagem(CORRECAO, 5, 7, { ramo: 'CORRECAO', responsaveis: [pessoa('Interessado (cadastrante)', null)] }),
            passagem({ id: 'aguardando_pagamento_correcao', rotulo: 'Aguardando pagamento (correção)', tipo: 'ESPERA' }, 7, 9, { ramo: 'CORRECAO' }),
            passagem(ANALISE, 9, null, { ciclo: 2, motivoDaVolta: { codigo: 'CORRECAO_DO_REQUERIMENTO', texto: 'depois da correção do requerimento' } }),
          ]),
        ),
      );
      const { raiz } = criar();

      expect(itens(raiz)[1].classList.contains('correcao')).toBe(true);
      expect(itens(raiz)[1].textContent).toContain('Ramo da correção');
      expect(itens(raiz)[2].classList.contains('correcao')).toBe(true);
      expect(itens(raiz)[3].classList.contains('correcao')).toBe(false);
      expect(itens(raiz)[3].querySelector('.tram-volta')!.textContent).toBe('2ª passagem: depois da correção do requerimento');
    });

    it('HC10: tarefas encerradas sem ação aparecem como tal, sem contar como concluídas', () => {
      api.tramitacao.mockReturnValue(
        of(
          tramitacao([
            passagem(ANALISE, 2, 5, {
              participantes: [pessoa('Analista', 'Servidor Exemplo 05'), pessoa('Analista', 'Servidor Exemplo 06', 'ENCERRADA_SEM_ACAO'), pessoa('Analista', 'Servidor Exemplo 07', 'ENCERRADA_SEM_ACAO')],
            }),
          ]),
        ),
      );
      const { raiz } = criar();
      const tags = Array.from(raiz.querySelectorAll('.tram-pessoas .tag')).map((t) => t.textContent);

      expect(tags).toEqual(['Concluiu', 'Encerrada sem ação', 'Encerrada sem ação']);
    });
  });

  describe('emissão do ato (HC12)', () => {
    it('lista os atos com o estado de cada um', () => {
      const atos: AtoDaPassagem[] = [
        { tipo: 'AUTORIZACAO', rotulo: 'Autorização', inicio: dia(5), fim: dia(6), estado: 'EMITIDO', ocorreu: true },
        { tipo: 'PORTARIA', rotulo: 'Portaria', inicio: dia(5), fim: dia(6), estado: 'EMITIDO', ocorreu: true },
        { tipo: 'OUTROS_ATOS', rotulo: 'Outros atos', inicio: dia(5), fim: dia(6), estado: 'NAO_CONCLUIDO', ocorreu: false },
      ];
      api.tramitacao.mockReturnValue(of(tramitacao([passagem(EMISSAO, 5, 6, { atos })])));
      const { raiz } = criar();
      const linhas = Array.from(raiz.querySelectorAll('.tram-atos li')).map((l) => l.textContent!.replace(/\s+/g, ' ').trim());

      expect(linhas).toEqual(['Autorização: Emitido', 'Portaria: Emitido', 'Outros atos: Não concluído']);
    });
  });

  describe('Operador (RN12) e complementação', () => {
    it('sem nome do servidor: só o papel, e nenhum nome aparece', () => {
      api.tramitacao.mockReturnValue(
        of(tramitacao([passagem(CONFERENCIA, 2, 3, { responsaveis: [pessoa('Conferidor', null)] }), passagem(ANALISE, 3, null, { participantes: [pessoa('Analista', null, 'PENDENTE')] })])),
      );
      const { raiz } = criar();

      expect(itens(raiz)[0].textContent).toContain('Conferidor');
      expect(texto(raiz)).not.toContain('Servidor Exemplo');
    });

    it('mostra a complementação paralela, concluída ou em andamento', () => {
      api.tramitacao.mockReturnValue(
        of(tramitacao([passagem(ANALISE, 2, null)], { complementacoes: [{ entrada: dia(4), saida: dia(6) }, { entrada: dia(8), saida: null }] })),
      );
      const t = texto(criar().raiz);

      expect(t).toContain('Complementação paralela: desde 04/03/2026');
      expect(t).toContain('até 06/03/2026');
      expect(t).toContain('em andamento');
    });
  });

  describe('avisos', () => {
    it('mostra o texto, a gravidade dita ao leitor de tela e o aviso de histórico truncado', () => {
      api.tramitacao.mockReturnValue(
        of(
          tramitacao([passagem(CADASTRO, 2, null)], {
            truncado: true,
            avisos: [{ codigo: 'HISTORICO_TRUNCADO', texto: 'Histórico incompleto.', gravidade: 'ATENCAO' }],
          }),
        ),
      );
      const { raiz } = criar();

      expect(texto(raiz)).toContain('Atenção: Histórico incompleto.');
    });
  });

  describe('recolher e expandir', () => {
    it('recolhe o corpo e mantém o botão acessível (aria-expanded e aria-controls)', () => {
      api.tramitacao.mockReturnValue(of(HC01));
      const { fixture, raiz } = criar();
      const botao = raiz.querySelector<HTMLButtonElement>('.tram-head button[aria-controls]')!;

      expect(botao.getAttribute('aria-expanded')).toBe('true');
      expect(raiz.querySelector('#' + botao.getAttribute('aria-controls'))).not.toBeNull();

      botao.click();
      fixture.detectChanges();

      expect(botao.getAttribute('aria-expanded')).toBe('false');
      expect(botao.textContent!.trim()).toBe('Expandir');
      expect(raiz.querySelector('.tram-lista')).toBeNull();
    });

    it('é uma seção com título e lista semântica', () => {
      api.tramitacao.mockReturnValue(of(HC01));
      const { raiz } = criar();
      const secao = raiz.querySelector('section')!;

      expect(raiz.querySelector('#' + secao.getAttribute('aria-labelledby'))!.textContent).toContain('Tramitação');
      expect(raiz.querySelector('ol.tram-lista')!.getAttribute('aria-label')).toContain('Etapas percorridas');
    });
  });

  describe('fluxograma e impressão (S8, bloco 9)', () => {
    it('o fluxograma vem acima da lista e a lista é o equivalente textual', () => {
      api.tramitacao.mockReturnValue(of(HC01));
      const { raiz } = criar();

      const fluxo = raiz.querySelector('app-fluxograma-simplificado')!;
      const lista = raiz.querySelector('ol.tram-lista')!;
      expect(fluxo.querySelectorAll('.caixa').length).toBeGreaterThan(0);
      expect(fluxo.compareDocumentPosition(lista) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(fluxo.querySelector('[role="group"]')!.getAttribute('aria-label')).toContain('A lista da seção Tramitação');
    });

    it('"Imprimir tramitação" marca o body só durante a impressão e chama window.print', () => {
      api.tramitacao.mockReturnValue(of(HC01));
      const imprimir = vi.spyOn(window, 'print').mockImplementation(() => {
        expect(document.body.classList.contains('imprimindo-tramitacao')).toBe(true);
      });
      const { raiz } = criar();
      const botao = Array.from(raiz.querySelectorAll<HTMLButtonElement>('.tram-acoes button')).find((b) => b.textContent!.includes('Imprimir tramitação'))!;

      botao.click();
      expect(imprimir).toHaveBeenCalledTimes(1);
      window.dispatchEvent(new Event('afterprint'));
      expect(document.body.classList.contains('imprimindo-tramitacao')).toBe(false);
      imprimir.mockRestore();
    });

    it('sem caminho a imprimir (indisponível, não suportado) não há botão nem fluxograma', () => {
      for (const estado of ['INDISPONIVEL', 'NAO_SUPORTADO'] as const) {
        TestBed.resetTestingModule();
        api.tramitacao.mockReturnValue(of(tramitacao([], { estado })));
        const { raiz } = criar();

        expect(texto(raiz.querySelector('.tram-acoes')!)).not.toContain('Imprimir');
        expect(raiz.querySelector('.fluxo')).toBeNull();
      }
    });
  });

  describe('estados', () => {
    it('carregando mostra o esqueleto e avisa o leitor de tela', () => {
      api.tramitacao.mockReturnValue(NEVER);
      const { raiz } = criar();

      expect(raiz.querySelector('section')!.getAttribute('aria-busy')).toBe('true');
      expect(texto(raiz)).toContain('Carregando a tramitação do requerimento');
    });

    it('erro HTTP mostra a mensagem e tenta de novo', () => {
      api.tramitacao.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 403, error: { errorCode: 'NAO_INTERESSADO' } })));
      const { fixture, raiz } = criar();

      expect(raiz.querySelector('[role="alert"]')).not.toBeNull();
      api.tramitacao.mockReturnValue(of(HC01));
      raiz.querySelector<HTMLButtonElement>('.tram-aviso-geral button')!.click();
      fixture.detectChanges();

      expect(rotulos(raiz)).toHaveLength(5);
    });

    it('INDISPONIVEL: aviso neutro, "Tentar novamente" e o resto da tela segue', () => {
      api.tramitacao.mockReturnValue(of(tramitacao([], { estado: 'INDISPONIVEL' })));
      const { raiz } = criar();

      expect(texto(raiz)).toContain('Não foi possível consultar a tramitação agora. O restante da página não foi afetado.');
      expect(texto(raiz)).toContain('Tentar novamente');
    });

    it('NAO_SUPORTADO', () => {
      api.tramitacao.mockReturnValue(of(tramitacao([], { estado: 'NAO_SUPORTADO', modelo: null })));

      expect(texto(criar().raiz)).toContain('Fluxo ainda não suportado pelo Rastro.');
    });

    it('SEM_INSTANCIA mostra o selo do histórico aproximado e a linha do tempo, sem fluxograma', () => {
      api.tramitacao.mockReturnValue(
        of(
          tramitacao([], {
            estado: 'SEM_INSTANCIA',
            aproximado: true,
            planoB: {
              selo: 'Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo',
              semDetalhamento: false,
              etapas: [{ etapa: CONFERENCIA, entrada: dia(3), saida: dia(5), eventos: 2 }],
              marcos: [{ tipo: 'ABERTURA', rotulo: 'Abertura do requerimento', data: dia(2) }],
              situacao: 'CONCLUIDO',
              eventosIgnorados: 0,
              eventosTruncados: false,
            },
          }),
        ),
      );
      const { raiz } = criar();

      expect(texto(raiz)).toContain('Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo');
      expect(rotulos(raiz)).toEqual(['Conferência dos documentos']);
      expect(texto(raiz)).toContain('Abertura do requerimento');
    });
  });
});
