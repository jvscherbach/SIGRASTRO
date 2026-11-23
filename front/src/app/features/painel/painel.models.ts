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
