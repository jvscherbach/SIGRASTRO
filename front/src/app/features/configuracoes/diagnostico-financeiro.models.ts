import { FinanceiroDoRequerimento } from '../detalhe/financeiro.models';

/**
 * Diagnóstico do financeiro (S9, bloco 10), só do Administrador e só leitura: quantos lançamentos a regra de validade
 * **ignorou**, por motivo, e **de onde veio a data** de cada "Isento de Taxa". Só contagens e enums: nenhum dado pessoal,
 * nenhum id de lançamento, nenhum DAR nem valor.
 */
export interface DiagnosticoDoFinanceiro {
  numeroRequerimento: string;
  /** Lançamentos mantidos (válidos pela regra, mais os mantidos com aviso). */
  validos: number;
  /** Nulo quando a resposta não trouxe o diagnóstico (só o Administrador o recebe). */
  ignorados: { motivo: string; texto: string; quantos: number }[] | null;
  isencoes: { ordem: number; origem: string; texto: string; naoProcessado: boolean }[];
  inconsistentes: number;
}

/** Motivos conhecidos de lançamento ignorado (docs/15); um motivo novo aparece com o código, sem esconder a contagem. */
export const TEXTO_DO_MOTIVO: Record<string, string> = {
  FALSO_SEM_DAR: 'Cancelado sem número de DAR (registro "falso", criado e descartado pelo sistema)',
  RUIDO_ISENTO_AGUARDANDO: 'Aguardando emissão marcado como isento (ruído, ignorado por ora)',
};

export const TEXTO_DA_ORIGEM: Record<string, string> = {
  LANCAMENTO: 'data da emissão do próprio lançamento',
  FLUXO: 'data da passagem "Isenção confirmada" da tramitação (a confirmar)',
  AUSENTE: 'nenhuma fonte tinha a data (data não registrada)',
};

/** Monta o diagnóstico a partir da resposta do financeiro; `null` quando a consulta está indisponível. */
export function diagnosticoDe(f: FinanceiroDoRequerimento & { ignoradosPorMotivo?: Record<string, number> }): DiagnosticoDoFinanceiro | null {
  if (f.estado === 'INDISPONIVEL') {
    return null;
  }
  const ignorados = f.ignoradosPorMotivo
    ? Object.entries(f.ignoradosPorMotivo).map(([motivo, quantos]) => ({ motivo, texto: TEXTO_DO_MOTIVO[motivo] ?? motivo, quantos }))
    : null;
  const isencoes = (f.isencoes as (typeof f.isencoes[number] & { origemDaData?: string; naoProcessado?: boolean })[]).map((i) => ({
    ordem: i.ordem,
    origem: i.origemDaData ?? '',
    texto: i.origemDaData ? (TEXTO_DA_ORIGEM[i.origemDaData] ?? i.origemDaData) : 'origem não informada na resposta',
    naoProcessado: i.naoProcessado === true,
  }));
  return {
    numeroRequerimento: f.numeroRequerimento,
    validos: f.lancamentos.length,
    ignorados,
    isencoes,
    inconsistentes: f.lancamentos.filter((l) => l.inconsistente).length,
  };
}
