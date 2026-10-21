/** Tipos do contrato da API usados por mais de uma feature (consulta e detalhe). */

export interface PageResponse<T> {
  conteudo: T[];
  pagina: number;
  tamanho: number;
  totalElementos: number;
  totalPaginas: number;
}

/** RN04 já resolvida pela API: "Processo" com o número do processo, ou "Requerimento" enquanto não protocolado. */
export interface Identificacao {
  rotulo: 'Processo' | 'Requerimento';
  numero: string;
  protocolado: boolean;
}

export interface Situacao {
  codigo: string;
  descricao: string;
}

export interface Interessado {
  nome: string | null;
  cpfCnpj: string | null;
}
