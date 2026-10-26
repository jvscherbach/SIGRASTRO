/** Contrato de GET /api/requerimentos/{id}/documentos e de /api/dossies (docs/07). O `id` do documento é texto. */

export type BloqueioDocumento = 'NAO_PDF' | 'INDISPONIVEL' | 'SIGILOSO';
export type SituacaoDocumento = 'VIGENTE' | 'SUBSTITUIDO' | 'INDISPONIVEL';
export type TipoArquivo = 'PDF' | 'PLANILHA' | 'TEXTO' | 'IMAGEM' | 'GEOMETRIA' | 'COMPACTADO' | 'DESCONHECIDO';

export interface DocumentoItem {
  origem: string;
  id: string;
  idRequerimento: number;
  /** "Documento sigiloso" quando mascarado para o perfil. */
  titulo: string | null;
  nomeArquivo: string | null;
  origemDescricao: string;
  data: string | null;
  situacao: SituacaoDocumento;
  sigiloso: boolean;
  acessivel: boolean;
  extensao: string | null;
  tipoArquivo: TipoArquivo | null;
  selecionavelPorPadrao: boolean;
  bloqueio: BloqueioDocumento | null;
}

export interface TotaisDocumentos {
  total: number;
  vigentes: number;
  /** Substituídos: chave válida e `DATA_FIM` preenchida. */
  semEfeito: number;
  naoPdf: number;
  /** Sem extensão ou extensão não reconhecida: o tipo real é conferido ao gerar o dossiê. */
  tipoNaoInformado: number;
  indisponiveis: number;
  sigilosos: number;
}

export interface DocumentosResposta {
  itens: DocumentoItem[];
  totais: TotaisDocumentos;
}

export interface ReferenciaDocumento {
  origem: string;
  id: string;
}

export type StatusDossie = 'PENDENTE' | 'PROCESSANDO' | 'CONCLUIDO' | 'FALHA' | 'CANCELADO' | 'EXPIRADO';
export type SituacaoItemDossie = 'AGUARDANDO' | 'OBTENDO' | 'OK' | 'FALHA';

export interface ErroDossie {
  origem: string | null;
  id: string | null;
  titulo: string | null;
  motivo: string;
  /** Só CREDENCIAL_ARQUIVOS_RECUSADA: vale para todos os documentos. */
  codigo?: string;
}

export interface ItemAndamento {
  origem: string;
  id: string;
  titulo: string;
  situacao: SituacaoItemDossie;
  /** Pedidos feitos à API de arquivos (S2.2; mais de 1 = houve nova tentativa). */
  tentativas?: number;
}

export interface AndamentoDossie {
  id: string;
  status: StatusDossie;
  total: number;
  processados: number;
  erro: ErroDossie | null;
  paginas: number | null;
  expiraEm: string | null;
  itens: ItemAndamento[];
}

export const STATUS_FINAIS: readonly StatusDossie[] = ['CONCLUIDO', 'FALHA', 'CANCELADO', 'EXPIRADO'];
