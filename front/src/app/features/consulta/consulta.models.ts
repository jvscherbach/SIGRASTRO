import { Identificacao, Interessado, Situacao } from '../../shared/api-tipos';

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
  interessado: Interessado | null;
  tipoProcesso: string | null;
  situacao: Situacao;
  abertura: string | null;
}
