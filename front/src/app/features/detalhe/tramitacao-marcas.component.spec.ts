import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { DetalheApiService } from './detalhe-api.service';
import { TramitacaoComponent } from './tramitacao.component';
import { EtapaDaPassagem, MarcaFinanceira, PassagemDaTramitacao, TramitacaoDoRequerimento } from './tramitacao.models';

/** S9, bloco 9: marcas financeiras na linha do tempo e no fluxograma, pela seção Tramitação inteira. */
const dia = (n: number): string => `2026-03-${String(n).padStart(2, '0')}T09:00:00`;
const etapa = (id: string, rotulo: string, tipo: EtapaDaPassagem['tipo'] = 'TAREFA_HUMANA'): EtapaDaPassagem => ({ id, rotulo, tipo });
const CADASTRO = etapa('cadastro_do_requerimento', 'Cadastro do requerimento');
const TAXA = etapa('emissao_da_taxa', 'Emissão da taxa', 'AUTOMATICA');
const PAGAMENTO = etapa('aguardando_pagamento', 'Aguardando pagamento', 'ESPERA');
const ANALISE = etapa('analise_do_processo', 'Análise do processo');
const CORRECAO = etapa('correcao_do_requerimento', 'Correção do requerimento');
const TAXA_CORRECAO = etapa('emissao_da_taxa_correcao', 'Emissão da taxa (correção)', 'AUTOMATICA');
const PAGAMENTO_CORRECAO = etapa('aguardando_pagamento_correcao', 'Aguardando pagamento (correção)', 'ESPERA');
const ISENCAO = etapa('isencao_confirmada', 'Isenção confirmada', 'AUTOMATICA');

const marca = (tipo: MarcaFinanceira['tipo'], rotulo: string, data: string | null, sobre: Partial<MarcaFinanceira> = {}): MarcaFinanceira => ({
  tipo,
  rotulo,
  data,
  valor: null,
  dataAConfirmar: false,
  ...sobre,
});

function p(e: EtapaDaPassagem, entrada: number, saida: number | null, sobre: Partial<PassagemDaTramitacao> = {}): PassagemDaTramitacao {
  return {
    etapa: e,
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

function t(passagens: PassagemDaTramitacao[], sobre: Partial<TramitacaoDoRequerimento> = {}): TramitacaoDoRequerimento {
  return {
    idRequerimento: 9011,
    numeroRequerimento: '9011/2026',
    numeroProcesso: '9900/2026',
    situacaoLicenciamento: { codigo: 'AGUARDANDO_PAGAMENTO_DE_TAXAS', descricao: 'Aguardando pagamento de taxas' },
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

/** HC07 + FIN11: taxa inicial paga e a da correção aguardando, já vencida por data. */
const FIN11 = t([
  p(CADASTRO, 1, 2),
  p(TAXA, 2, 2, { marcasFinanceiras: [marca('EMITIDA_EM', 'Emitida em', '2026-03-02'), marca('NUMERO_DAR', 'DAR', null, { valor: 'FICT-0011' })] }),
  p(PAGAMENTO, 2, 5, { marcasFinanceiras: [marca('VENCE_EM', 'Vence em', '2026-03-12'), marca('PAGA_EM', 'Paga em', '2026-03-05')] }),
  p(ANALISE, 5, 8),
  p(CORRECAO, 8, 10, { ramo: 'CORRECAO' }),
  p(TAXA_CORRECAO, 10, 10, {
    ramo: 'CORRECAO',
    marcasFinanceiras: [
      marca('EMITIDA_EM', 'Emitida em', '2026-03-10'),
      marca('NUMERO_DAR', 'DAR', null, { valor: 'FICT-0111' }),
      marca('DA_CORRECAO', 'da correção', null),
    ],
  }),
  p(PAGAMENTO_CORRECAO, 10, null, {
    ramo: 'CORRECAO',
    marcasFinanceiras: [
      marca('VENCE_EM', 'Vence em', '2026-03-19'),
      marca('TAXA_VENCIDA', 'Taxa vencida', '2026-03-19'),
      marca('DA_CORRECAO', 'da correção', null),
    ],
  }),
]);

describe('TramitacaoComponent: marcas financeiras (S9, bloco 9)', () => {
  function criar(tramitacao: TramitacaoDoRequerimento): HTMLElement {
    TestBed.configureTestingModule({
      imports: [TramitacaoComponent],
      providers: [{ provide: DetalheApiService, useValue: { tramitacao: vi.fn(() => of(tramitacao)) } }],
    });
    const fixture = TestBed.createComponent(TramitacaoComponent);
    fixture.componentRef.setInput('idRequerimento', 9011);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const limpo = (el: Element) => el.textContent!.replace(/\s+/g, ' ').trim();
  const item = (raiz: HTMLElement, rotulo: string, nth = 0) =>
    Array.from(raiz.querySelectorAll('.tram-lista > li')).filter((li) => li.querySelector('.tram-titulo b')!.textContent!.trim() === rotulo)[nth]!;
  const marcasDe = (li: Element) => Array.from(li.querySelectorAll('.tram-marcas-fin li')).map(limpo);

  describe('linha do tempo', () => {
    it('cada passagem de taxa mostra suas marcas, com a etiqueta "da correção" no ramo de correção', () => {
      const raiz = criar(FIN11);

      expect(marcasDe(item(raiz, 'Emissão da taxa'))).toEqual(['Emitida em 02/03/2026', 'DAR FICT-0011']);
      expect(marcasDe(item(raiz, 'Aguardando pagamento'))).toEqual(['Vence em 12/03/2026', 'Paga em 05/03/2026']);
      expect(marcasDe(item(raiz, 'Emissão da taxa (correção)'))).toEqual(['Emitida em 10/03/2026', 'DAR FICT-0111', 'da correção']);
      expect(marcasDe(item(raiz, 'Aguardando pagamento (correção)'))).toEqual([
        'Vence em 19/03/2026',
        'Taxa vencida em 19/03/2026',
        'da correção',
      ]);
      // Passagem sem marca não ganha nada.
      expect(marcasDe(item(raiz, 'Análise do processo'))).toEqual([]);
    });

    it('com "ocultar etapas automáticas" ligado (padrão), a emissão da taxa continua à vista porque traz as marcas', () => {
      const raiz = criar(FIN11);

      expect(raiz.querySelector<HTMLInputElement>('.tram-filtro input')!.checked).toBe(true);
      const rotulos = Array.from(raiz.querySelectorAll('.tram-lista > li .tram-titulo b')).map(limpo);
      expect(rotulos).toContain('Emissão da taxa');
      expect(rotulos).toContain('Emissão da taxa (correção)');
    });

    it('"Isento de Taxa em" na "Isenção confirmada", com a nota quando a data é provisória', () => {
      const raiz = criar(
        t([
          p(ISENCAO, 2, 2, { marcasFinanceiras: [marca('ISENTO_DE_TAXA', 'Isento de Taxa', '2026-03-02')] }),
          p(ISENCAO, 3, 3, { ciclo: 2, marcasFinanceiras: [marca('ISENTO_DE_TAXA', 'Isento de Taxa', '2026-03-03', { dataAConfirmar: true })] }),
          p(ANALISE, 3, null),
        ]),
      );

      expect(marcasDe(item(raiz, 'Isenção confirmada', 0))).toEqual(['Isento de Taxa em 02/03/2026']);
      expect(marcasDe(item(raiz, 'Isenção confirmada', 1))).toEqual(['Isento de Taxa em 03/03/2026 (data a confirmar)']);
    });

    it('sem marcas (Camunda fora ou sem lançamento) a lista fica como antes', () => {
      const raiz = criar(t([p(CADASTRO, 1, 2), p(TAXA, 2, 2), p(PAGAMENTO, 2, null)]));

      expect(raiz.querySelector('.tram-marcas-fin')).toBeNull();
    });

    it('plano B: nenhuma marca, mesmo que a resposta trouxesse', () => {
      const raiz = criar(
        t([p(TAXA, 2, 2, { marcasFinanceiras: [marca('EMITIDA_EM', 'Emitida em', '2026-03-02')] })], {
          estado: 'SEM_INSTANCIA',
          aproximado: true,
          planoB: {
            selo: 'Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo',
            semDetalhamento: false,
            etapas: [{ etapa: PAGAMENTO, entrada: dia(2), saida: null, eventos: 1 }],
            marcos: [],
            situacao: null,
            eventosIgnorados: 0,
            eventosTruncados: false,
          },
        }),
      );

      expect(raiz.querySelector('.tram-marcas-fin')).toBeNull();
      expect(limpo(raiz)).not.toContain('Emitida em');
    });
  });

  describe('fluxograma', () => {
    it('a caixa "Aguardando pagamento" mostra a data do pagamento e a da correção, o vencimento e o selo "Taxa vencida"', () => {
      const raiz = criar(FIN11);

      const caixas = Array.from(raiz.querySelectorAll('.fluxo .caixa'));
      const daEtapa = (rotulo: string) => caixas.find((c) => c.querySelector('.caixa-rotulo')!.textContent!.trim() === rotulo)!;
      expect(limpo(daEtapa('Aguardando pagamento').querySelector('.caixa-fin')!)).toBe('Paga em 05/03/2026');
      expect(daEtapa('Aguardando pagamento').querySelector('.caixa-vencida')).toBeNull();
      expect(limpo(daEtapa('Aguardando pagamento (correção)').querySelector('.caixa-fin')!)).toBe('Vence em 19/03/2026');
      expect(limpo(daEtapa('Aguardando pagamento (correção)').querySelector('.caixa-vencida')!)).toContain('Taxa vencida');
      // Nenhuma caixa nova e nenhuma etapa futura: as caixas são as passagens de usuário que já aconteceram.
      expect(caixas.map((c) => limpo(c.querySelector('.caixa-rotulo')!))).toEqual([
        'Cadastro do requerimento',
        'Emissão da taxa',
        'Aguardando pagamento',
        'Análise do processo',
        'Correção do requerimento',
        'Emissão da taxa (correção)',
        'Aguardando pagamento (correção)',
      ]);
    });

    it('"Isenção confirmada" mostra "Isento de Taxa em dd/mm/aaaa" na caixa', () => {
      const raiz = criar(t([p(ISENCAO, 2, 2, { marcasFinanceiras: [marca('ISENTO_DE_TAXA', 'Isento de Taxa', '2026-03-02')] }), p(ANALISE, 2, null)]));

      const caixa = Array.from(raiz.querySelectorAll('.fluxo .caixa')).find((c) => limpo(c).startsWith('Isenção confirmada'))!;
      expect(limpo(caixa.querySelector('.caixa-fin')!)).toBe('Isento de Taxa em 02/03/2026');
    });

    it('a dica (tooltip) lista todas as marcas da passagem', () => {
      const raiz = criar(FIN11);

      const caixa = Array.from(raiz.querySelectorAll('.fluxo .caixa')).find((c) => limpo(c.querySelector('.caixa-rotulo')!) === 'Emissão da taxa')!;
      expect(limpo(caixa.querySelector('.dica')!)).toContain('Emitida em 02/03/2026');
      expect(limpo(caixa.querySelector('.dica')!)).toContain('DAR FICT-0011');
    });
  });
});
