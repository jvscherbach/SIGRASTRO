/** Contrato de `/api/caixa` (S11, docs/07). Só o nome do interessado: nunca CPF, CNPJ nem chave do analista. */
export type EstadoDaCaixa = 'PREPARANDO' | 'PRONTO' | 'DESATUALIZADO' | 'INDISPONIVEL';
export type OrdemDaCaixa = 'MAIS_PARADO' | 'MAIS_RECENTE';

/** Id da etapa de correção (com o interessado): também é um filtro da lista. */
export const ETAPA_CORRECAO = 'correcao_do_requerimento';

export interface EtapaDoResumo {
  id: string;
  rotulo: string;
  quantidade: number;
}

export interface ResumoDaCaixa {
  total: number;
  comInteressado: number;
  porEtapa: EtapaDoResumo[];
}

export interface LinhaDaCaixa {
  id: number;
  numero: string | null;
  interessado: string | null;
  tipo: string | null;
  posse: 'COM_ANALISTA' | 'COM_INTERESSADO';
  etapaId: string;
  etapa: string;
  diasNaEtapa: number | null;
  novo: boolean;
}

export interface CaixaResposta {
  estado: EstadoDaCaixa;
  geradoEm: string | null;
  falhouEm: string | null;
  incompleta: boolean;
  semVinculo: boolean;
  mensagem: string | null;
  novos: number;
  resumo: ResumoDaCaixa;
  linhas: LinhaDaCaixa[];
}

export interface AvisoDaCaixa {
  id: number;
  numero: string | null;
  interessado: string | null;
  tipo: string | null;
  etapaId: string;
  etapa: string;
  origemCodigo: string;
  origem: string;
}

export interface AvisosResposta {
  semVinculo: boolean;
  mensagem: string | null;
  quantidade: number;
  avisos: AvisoDaCaixa[];
}

export interface FiltroDaCaixa {
  etapa: string;
  busca: string;
  ordem: OrdemDaCaixa;
}
