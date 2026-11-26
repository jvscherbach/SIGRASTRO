/** Contrato de GET /api/painel (S10, docs/07). Só números, rótulos e avisos: nunca CPF nem id técnico. */
export type EstadoDoPainel = 'PREPARANDO' | 'PRONTO' | 'DESATUALIZADO' | 'INDISPONIVEL';

export type TipoDeCarga = 'SERVIDOR' | 'AREA' | 'INTERESSADO' | 'NAO_ATRIBUIDA' | 'NAO_IDENTIFICADO' | 'OUTROS';

export interface MesDoPainel {
  mes: number;
  protocolados: number;
  concluidos: number;
}

export interface EtapaDoPainel {
  rotulo: string;
  total: number;
}

export interface CargaDoPainel {
  rotulo: string;
  total: number;
  tipo: TipoDeCarga;
}

export interface AvisoDoPainel {
  codigo: string;
  texto: string;
}

/** Contrato de GET /api/painel/analistas (S11, bloco 9): `analista` é um identificador opaco, nunca CPF. */
export interface AnalistaDoPainel {
  analista: string;
  rotulo: string;
  total: number;
  tipo: TipoDeCarga;
}

export interface AnalistasDoPainelResposta {
  estado: EstadoDoPainel;
  ano: number;
  geradoEm: string | null;
  falhouEm: string | null;
  truncado: boolean;
  nomesOcultos: boolean;
  analistas: AnalistaDoPainel[];
  etapas: string[];
  avisos: AvisoDoPainel[];
}

export interface RequerimentoDoAnalista {
  id: number;
  numero: string | null;
  interessado: string | null;
  protocolo: string | null;
  etapas: string[];
  responsavel: string;
}

/** Contrato de GET /api/painel/analistas/{chave}/requerimentos: `total` é o da barra; `quantidade` é o que sobrou dos filtros. */
export interface RequerimentosDoAnalistaResposta {
  estado: EstadoDoPainel;
  ano: number;
  geradoEm: string | null;
  truncado: boolean;
  analista: string;
  rotulo: string;
  tipo: TipoDeCarga;
  total: number;
  quantidade: number;
  limitada: boolean;
  requerimentos: RequerimentoDoAnalista[];
  avisos: AvisoDoPainel[];
}

export interface PainelResposta {
  estado: EstadoDoPainel;
  ano: number;
  geradoEm: string | null;
  falhouEm: string | null;
  proximaAtualizacaoEm: string | null;
  truncado: boolean;
  serie: MesDoPainel[];
  totalProtocolados: number;
  totalConcluidos: number;
  semDataDeProtocolo: number;
  filaPorEtapa: EtapaDoPainel[];
  totalNaFila: number;
  foraDoCatalogo: number;
  semInstancia: number;
  etapaNaoMapeada: number;
  cargaPorResponsavel: CargaDoPainel[];
  nomesOcultos: boolean;
  avisos: AvisoDoPainel[];
}
