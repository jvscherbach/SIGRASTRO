/**
 * Histórico financeiro do requerimento (S9; `GET /api/requerimentos/{id}/financeiro`, docs/07 "Financeiro"). Os campos só do
 * Administrador (`idTecnico`, `statusProcessado`, `passouPelaReaprovacaoDaCorrecao`, `inconsistencias`, `origemDaData`,
 * `naoProcessado`, `ignoradosPorMotivo`) ficam de fora de propósito: a tela nunca mostra id técnico nem diagnóstico.
 */
export type EstadoDoFinanceiro = 'ATIVO' | 'INDISPONIVEL';

export type ResumoFinanceiro = 'SEM_TAXA' | 'ISENTO' | 'EM_ABERTO' | 'QUITADO' | 'VENCIDO' | 'INDETERMINADO';

export type SituacaoDoLancamento =
  | 'EMITINDO'
  | 'AGUARDANDO_PAGAMENTO'
  | 'PAGA'
  | 'VENCIDA'
  | 'ISENTA'
  | 'CANCELADA'
  | 'DESCONHECIDA';

export interface LancamentoFinanceiro {
  ordem: number;
  /** "1ª taxa", "2ª taxa" ou "Taxa da correção"; nulo na isenção e no status desconhecido. */
  rotulo: string | null;
  /** A do Licenciamento, como está: o Rastro não a troca. */
  situacao: SituacaoDoLancamento;
  vencida: boolean;
  /** Vencida só pela data: o SIGA ainda não a marcou. */
  vencidaPorData: boolean;
  inconsistente: boolean;
  /** Datas puras (aaaa-mm-dd), sem fuso. */
  emitidaEm: string | null;
  venceEm: string | null;
  pagaEm: string | null;
  numeroDar: string | null;
  /** Ausentes com o parâmetro de valores desligado, e quando o SIGA não informou ("não informado", nunca zero). */
  valor?: number | null;
  desconto?: number | null;
  upf?: number | null;
}

export interface IsencaoFinanceira {
  texto: string;
  ordem: number;
  /** Nula quando nenhuma fonte tinha a data. */
  data: string | null;
  /** A data não é a do próprio lançamento (ou não existe). */
  dataAConfirmar: boolean;
}

export interface AvisoFinanceiro {
  codigo: string;
  texto: string;
  gravidade: 'INFO' | 'ATENCAO';
}

export interface FinanceiroDoRequerimento {
  idRequerimento: number;
  numeroRequerimento: string;
  numeroProcesso: string | null;
  estado: EstadoDoFinanceiro;
  /** Nulo quando `INDISPONIVEL`. */
  resumo: ResumoFinanceiro | null;
  /** Valor, desconto e UPF existem nesta resposta (`RASTRO_MOSTRAR_VALORES`). */
  mostraValores: boolean;
  lancamentos: LancamentoFinanceiro[];
  isencoes: IsencaoFinanceira[];
  avisos: AvisoFinanceiro[];
}

export type TomDoSelo = 'gray' | 'lime' | 'amber' | 'red' | 'blue';

/** Selo do resumo: texto e ícone (nunca só a cor). */
export const SELO_DO_RESUMO: Record<ResumoFinanceiro, { texto: string; icone: string; tom: TomDoSelo }> = {
  SEM_TAXA: { texto: 'Sem taxa', icone: 'info', tom: 'gray' },
  ISENTO: { texto: 'Isento de taxa', icone: 'check', tom: 'blue' },
  EM_ABERTO: { texto: 'Em aberto', icone: 'clock', tom: 'amber' },
  QUITADO: { texto: 'Quitado', icone: 'check', tom: 'lime' },
  VENCIDO: { texto: 'Vencido', icone: 'alert', tom: 'red' },
  INDETERMINADO: { texto: 'Situação não determinada', icone: 'info', tom: 'gray' },
};

export const ROTULO_DA_SITUACAO: Record<SituacaoDoLancamento, string> = {
  EMITINDO: 'Aguardando emissão',
  AGUARDANDO_PAGAMENTO: 'Aguardando pagamento',
  PAGA: 'Paga',
  VENCIDA: 'Vencida',
  ISENTA: 'Isenta',
  CANCELADA: 'Cancelada',
  DESCONHECIDA: 'Situação não reconhecida',
};

export function tomDaSituacao(situacao: SituacaoDoLancamento): TomDoSelo {
  switch (situacao) {
    case 'PAGA':
      return 'lime';
    case 'AGUARDANDO_PAGAMENTO':
    case 'EMITINDO':
      return 'amber';
    case 'VENCIDA':
      return 'red';
    case 'ISENTA':
      return 'blue';
    default:
      return 'gray';
  }
}

/** Nome do lançamento na lista: "1ª taxa", "Taxa da correção" ou, sem número de taxa, a própria situação. */
export function nomeDoLancamento(l: LancamentoFinanceiro): string {
  if (l.rotulo) {
    return l.rotulo;
  }
  return l.situacao === 'ISENTA' ? 'Isenção' : 'Lançamento';
}
