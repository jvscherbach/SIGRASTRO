import { montarFluxograma } from './fluxograma.models';
import { EtapaDaPassagem, MarcaFinanceira, PassagemDaTramitacao, TramitacaoDoRequerimento } from './tramitacao.models';

/** S9, bloco 9: as marcas financeiras nas caixas do fluxograma (nenhuma caixa nova, nenhuma etapa futura). */
const dia = (n: number): string => `2026-03-${String(n).padStart(2, '0')}T09:00:00`;
const etapa = (id: string, rotulo: string, tipo: EtapaDaPassagem['tipo'] = 'TAREFA_HUMANA'): EtapaDaPassagem => ({ id, rotulo, tipo });
const TAXA = etapa('emissao_da_taxa', 'Emissão da taxa', 'AUTOMATICA');
const PAGAMENTO = etapa('aguardando_pagamento', 'Aguardando pagamento', 'ESPERA');
const ISENCAO = etapa('isencao_confirmada', 'Isenção confirmada', 'AUTOMATICA');
const ANALISE = etapa('analise_do_processo', 'Análise do processo');
const CORRECAO = etapa('correcao_do_requerimento', 'Correção do requerimento');

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
    numeroProcesso: null,
    situacaoLicenciamento: null,
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

const marca = (tipo: MarcaFinanceira['tipo'], rotulo: string, data: string | null, sobre: Partial<MarcaFinanceira> = {}): MarcaFinanceira => ({
  tipo,
  rotulo,
  data,
  valor: null,
  dataAConfirmar: false,
  ...sobre,
});

const caixas = (f: ReturnType<typeof montarFluxograma>) => f.itens.flatMap((i) => (i.tipo === 'caixa' ? [i.caixa] : i.caixas));
const daEtapa = (f: ReturnType<typeof montarFluxograma>, rotulo: string) => caixas(f).find((c) => c.rotulo === rotulo)!;

describe('marcas financeiras na caixa (S9, bloco 9)', () => {
  it('HC07 + FIN11: a espera paga mostra "Paga em", sem selo; a da correção, sem pagamento, mostra "Vence em" e a taxa vencida', () => {
    const f = montarFluxograma(
      t([
        p(TAXA, 2, 2, {
          marcasFinanceiras: [marca('EMITIDA_EM', 'Emitida em', '2026-03-02'), marca('NUMERO_DAR', 'DAR', null, { valor: 'FICT-0011' })],
        }),
        p(PAGAMENTO, 2, 5, { marcasFinanceiras: [marca('VENCE_EM', 'Vence em', '2026-03-12'), marca('PAGA_EM', 'Paga em', '2026-03-05')] }),
        p(CORRECAO, 5, 7, { ramo: 'CORRECAO' }),
        p(etapa('emissao_da_taxa_correcao', 'Emissão da taxa (correção)', 'AUTOMATICA'), 7, 7, { ramo: 'CORRECAO' }),
        p(etapa('aguardando_pagamento_correcao', 'Aguardando pagamento (correção)', 'ESPERA'), 7, null, {
          ramo: 'CORRECAO',
          marcasFinanceiras: [
            marca('VENCE_EM', 'Vence em', '2026-03-19'),
            marca('TAXA_VENCIDA', 'Taxa vencida', '2026-03-19'),
            marca('DA_CORRECAO', 'da correção', null),
          ],
        }),
      ]),
    );

    expect(daEtapa(f, 'Aguardando pagamento').financeiro).toBe('Paga em 05/03/2026');
    expect(daEtapa(f, 'Aguardando pagamento').taxaVencida).toBe(false);
    expect(daEtapa(f, 'Aguardando pagamento (correção)').financeiro).toBe('Vence em 19/03/2026');
    expect(daEtapa(f, 'Aguardando pagamento (correção)').taxaVencida).toBe(true);
    // Nenhuma caixa nova: as mesmas de antes, e a emissão da taxa não ganha linha financeira (só a dica).
    expect(caixas(f).map((c) => c.rotulo)).toEqual([
      'Emissão da taxa',
      'Aguardando pagamento',
      'Correção do requerimento',
      'Emissão da taxa (correção)',
      'Aguardando pagamento (correção)',
    ]);
    expect(daEtapa(f, 'Emissão da taxa').financeiro).toBeNull();
    expect(daEtapa(f, 'Emissão da taxa').dica).toEqual(expect.arrayContaining(['Emitida em 02/03/2026', 'DAR FICT-0011']));
  });

  it('H18/FIN15: a espera encerrada por taxa não paga leva o vencimento e o selo "Taxa vencida"', () => {
    const f = montarFluxograma(
      t(
        [
          p(PAGAMENTO, 4, 19, {
            marcasFinanceiras: [marca('VENCE_EM', 'Vence em', '2026-03-19'), marca('TAXA_VENCIDA', 'Taxa vencida', '2026-03-19')],
          }),
        ],
        { estado: 'ENCERRADO', final: { rotulo: 'Encerrado por taxa não paga no prazo', tipo: 'FINAL', encerradoEm: dia(19) } },
      ),
    );

    expect(daEtapa(f, 'Aguardando pagamento').financeiro).toBe('Vence em 19/03/2026');
    expect(daEtapa(f, 'Aguardando pagamento').taxaVencida).toBe(true);
    expect(f.final?.rotulo).toBe('Encerrado por taxa não paga no prazo');
  });

  it('"Isenção confirmada" mostra "Isento de Taxa em", com "(data a confirmar)" quando a data é provisória', () => {
    const f = montarFluxograma(
      t([
        p(ISENCAO, 2, 2, { marcasFinanceiras: [marca('ISENTO_DE_TAXA', 'Isento de Taxa', '2026-03-02')] }),
        p(ISENCAO, 3, 3, { marcasFinanceiras: [marca('ISENTO_DE_TAXA', 'Isento de Taxa', '2026-03-03', { dataAConfirmar: true })] }),
        p(ANALISE, 3, null),
      ]),
    );

    const isencoes = caixas(f).filter((c) => c.rotulo === 'Isenção confirmada');
    expect(isencoes.map((c) => c.financeiro)).toEqual(['Isento de Taxa em 02/03/2026', 'Isento de Taxa em 03/03/2026 (data a confirmar)']);
  });

  it('sem marcas (plano B, Camunda fora ou sem lançamento) nada é mostrado nem inventado', () => {
    const f = montarFluxograma(t([p(ISENCAO, 1, 1), p(TAXA, 2, 2), p(PAGAMENTO, 2, null)]));

    for (const c of caixas(f)) {
      expect(c.financeiro).toBeNull();
      expect(c.taxaVencida).toBe(false);
    }
    expect(caixas(f).map((c) => c.rotulo)).toEqual(['Isenção confirmada', 'Emissão da taxa', 'Aguardando pagamento']);
  });
});
