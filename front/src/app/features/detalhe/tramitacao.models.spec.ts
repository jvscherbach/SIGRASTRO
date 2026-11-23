import { formatarData } from '../../shared/formato';
import {
  EtapaDaPassagem,
  MarcaFinanceira,
  PassagemDaTramitacao,
  marcaPedeConfirmacao,
  marcasDe,
  passagensVisiveis,
  textoDaMarca,
} from './tramitacao.models';

const marca = (tipo: MarcaFinanceira['tipo'], rotulo: string, data: string | null, sobre: Partial<MarcaFinanceira> = {}): MarcaFinanceira => ({
  tipo,
  rotulo,
  data,
  valor: null,
  dataAConfirmar: false,
  ...sobre,
});

const etapa = (id: string, rotulo: string, tipo: EtapaDaPassagem['tipo']): EtapaDaPassagem => ({ id, rotulo, tipo });

function passagem(e: EtapaDaPassagem, sobre: Partial<PassagemDaTramitacao> = {}): PassagemDaTramitacao {
  return {
    etapa: e,
    ciclo: 1,
    ramo: 'PRINCIPAL',
    entrada: '2026-03-02T09:00:00',
    saida: '2026-03-03T09:00:00',
    duracaoSegundos: 86400,
    atual: false,
    emParalelo: false,
    processoConcluido: false,
    motivoDaVolta: null,
    responsaveis: [],
    participantes: [],
    atos: [],
    ...sobre,
  };
}

describe('marcas financeiras (S9, bloco 9)', () => {
  const texto = (m: MarcaFinanceira) => textoDaMarca(m, formatarData);

  it('cada marca tem seu texto, com a data pura sem deslocar o dia', () => {
    expect(texto(marca('EMITIDA_EM', 'Emitida em', '2026-03-04'))).toBe('Emitida em 04/03/2026');
    expect(texto(marca('NUMERO_DAR', 'DAR', null, { valor: 'FICT-0001' }))).toBe('DAR FICT-0001');
    expect(texto(marca('VENCE_EM', 'Vence em', '2026-03-01'))).toBe('Vence em 01/03/2026');
    expect(texto(marca('PAGA_EM', 'Paga em', '2026-03-31'))).toBe('Paga em 31/03/2026');
    expect(texto(marca('TAXA_VENCIDA', 'Taxa vencida', '2026-03-19'))).toBe('Taxa vencida em 19/03/2026');
    expect(texto(marca('TAXA_VENCIDA', 'Taxa vencida', null))).toBe('Taxa vencida');
    expect(texto(marca('DA_CORRECAO', 'da correção', null))).toBe('da correção');
  });

  it('"Isento de Taxa" com data, sem data e a nota de confirmação', () => {
    expect(texto(marca('ISENTO_DE_TAXA', 'Isento de Taxa', '2026-03-04'))).toBe('Isento de Taxa em 04/03/2026');
    expect(texto(marca('ISENTO_DE_TAXA', 'Isento de Taxa', null, { dataAConfirmar: true }))).toBe('Isento de Taxa — data não registrada');
    expect(marcaPedeConfirmacao(marca('ISENTO_DE_TAXA', 'Isento de Taxa', '2026-03-04', { dataAConfirmar: true }))).toBe(true);
    expect(marcaPedeConfirmacao(marca('ISENTO_DE_TAXA', 'Isento de Taxa', '2026-03-04'))).toBe(false);
    // Sem data não há o que confirmar: o texto já diz "data não registrada".
    expect(marcaPedeConfirmacao(marca('ISENTO_DE_TAXA', 'Isento de Taxa', null, { dataAConfirmar: true }))).toBe(false);
  });

  it('resposta sem o campo equivale a sem marcas', () => {
    expect(marcasDe(passagem(etapa('analise_do_processo', 'Análise', 'TAREFA_HUMANA')))).toEqual([]);
  });

  it('o filtro de automáticas mantém a que traz marca financeira, a atual e as humanas; some a automática sem marca', () => {
    const taxa = passagem(etapa('emissao_da_taxa', 'Emissão da taxa', 'AUTOMATICA'), {
      marcasFinanceiras: [marca('EMITIDA_EM', 'Emitida em', '2026-03-04')],
    });
    const semMarca = passagem(etapa('script', 'Script', 'AUTOMATICA'));
    const humana = passagem(etapa('analise_do_processo', 'Análise', 'TAREFA_HUMANA'));
    const atual = passagem(etapa('outra', 'Outra', 'AUTOMATICA'), { atual: true, saida: null });

    expect(passagensVisiveis([taxa, semMarca, humana, atual], true)).toEqual([taxa, humana, atual]);
    expect(passagensVisiveis([taxa, semMarca, humana, atual], false)).toHaveLength(4);
  });
});
