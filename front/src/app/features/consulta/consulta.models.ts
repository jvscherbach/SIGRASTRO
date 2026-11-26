import { Identificacao, Situacao } from '../../shared/api-tipos';

export interface InteressadoDaLista {
  nome: string | null;
}

export type TipoBusca = 'REQUERIMENTO' | 'PROCESSO';

/** Critérios já validados e normalizados (CPF/CNPJ só com dígitos). */
export interface CriterioBusca {
  tipo?: TipoBusca;
  numero?: string;
  cpfCnpj?: string;
  nome?: string;
  pagina: number;
  tamanho?: number;
}

export interface ResultadoBusca {
  id: number;
  identificacao: Identificacao;
  numeroRequerimento: string;
  numeroProcesso: string | null;
  /** Só o nome (pessoa) ou a razão social (empresa): a listagem nunca traz CPF/CNPJ (S11). */
  interessado: InteressadoDaLista | null;
  tipoProcesso: string | null;
  situacao: Situacao;
  abertura: string | null;
}
