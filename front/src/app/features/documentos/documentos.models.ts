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
  /** S3: planilhas, textos e imagens convertíveis para PDF que o perfil pode selecionar (0 com a conversão desligada). */
  convertiveis: number;
  /** S4: tamanho de `geometrias` (fora de todos os totais acima). */
  geometrias?: number;
  /** S4: geometrias sigilosas que o perfil não vê (Operador); só a contagem, nunca nome ou data. */
  geometriasOmitidas?: number;
}

/** Geometria (SHP, KML, KMZ) em lista própria (S4, RN10): fora da seleção, dos totais e do dossiê. */
export interface GeometriaItem {
  origem: string;
  id: string;
  idRequerimento: number;
  titulo: string | null;
  nomeArquivo: string | null;
  origemDescricao: string;
  data: string | null;
  situacao: SituacaoDocumento;
  sigiloso: boolean;
  extensao: string | null;
  tipoArquivo: TipoArquivo | null;
  /** Bytes; null enquanto desconhecido (o SIGA não informa). */
  tamanho: number | null;
}

/** Arquivo que impediu o ZIP (GEOMETRIAS_INCOMPLETAS): nome no ZIP e motivo exibível, nunca a chave. */
export interface GeometriaComProblema {
  nome: string;
  motivo: string;
}

/** Aviso da lista (S4): SCORP_INDISPONIVEL ou SCORP_PARCIAL. Nunca impede selecionar ou gerar. */
export interface AvisoDaLista {
  codigo: string;
  mensagem: string;
}

export interface DocumentosResposta {
  itens: DocumentoItem[];
  totais: TotaisDocumentos;
  avisos?: AvisoDaLista[];
  /** S4: geometrias do requerimento que o perfil pode ver. */
  geometrias?: GeometriaItem[];
}

export interface ReferenciaDocumento {
  origem: string;
  id: string;
}

/**
 * Situações do pedido (S2.2): falhas de documento deixam o pedido em PENDENTE_DECISAO, sem reiniciar nada. FALHA (S6):
 * sem espaço em disco ou arquivo final perdido; não há arquivo e o caminho é gerar de novo.
 */
export type StatusDossie =
  | 'AGUARDANDO'
  | 'OBTENDO'
  | 'PENDENTE_DECISAO'
  | 'MONTANDO'
  | 'CONCLUIDO'
  | 'CANCELADO'
  | 'EXPIRADO'
  | 'FALHA';
export type SituacaoItemDossie = 'AGUARDANDO' | 'OBTENDO' | 'OK' | 'FALHA' | 'REMOVIDO';

export type AcaoDeContinuacao = 'TENTAR_NOVAMENTE' | 'REMOVER_E_CONTINUAR';

/** Documento com problema num pedido que aguarda decisão. */
export interface FalhaDossie {
  origem: string;
  id: string;
  titulo: string;
  motivo: string;
}

export interface ErroDossie {
  origem: string | null;
  id: string | null;
  titulo: string | null;
  motivo: string;
  /** CREDENCIAL_ARQUIVOS_RECUSADA (vale para todos os documentos) ou o motivo da FALHA (S6: ARMAZENAMENTO_INSUFICIENTE, DOSSIE_ARQUIVO_AUSENTE, DOSSIE_ARQUIVO_INVALIDO). */
  codigo?: string;
}

export interface ItemAndamento {
  origem: string;
  id: string;
  titulo: string;
  situacao: SituacaoItemDossie;
  /** Pedidos feitos à API de arquivos (S2.2; mais de 1 = houve nova tentativa). */
  tentativas?: number;
  /** Só em FALHA. */
  motivo?: string;
}

export interface AndamentoDossie {
  id: string;
  status: StatusDossie;
  total: number;
  processados: number;
  /** Motivo geral: credencial recusada (com codigo) ou falha na montagem. */
  erro: ErroDossie | null;
  /** Itens em FALHA (S2.2). */
  falhas: FalhaDossie[];
  paginas: number | null;
  /** Só em CONCLUIDO. */
  tamanhoBytes?: number | null;
  /** Acima de DOSSIE_BLOB_MAX_MB: download direto por ticket, sem Blob nem pré-visualização (S2.2, D49). */
  entregaPorTicket?: boolean;
  expiraEm: string | null;
  itens: ItemAndamento[];
}

/** Situação na lista de dossiês gerados (S4): as do andamento mais BAIXADO (entregue e apagado, RNF05). */
export type SituacaoDossieGerado = StatusDossie | 'BAIXADO';

/** Item de GET /api/dossies (S4, docs/07). Sem dado de documento; `geradoPor` só vem para Gestor e Administrador. */
export interface DossieGerado {
  id: string;
  idRequerimento: number;
  identificacao: { rotulo: string; numero: string };
  situacao: SituacaoDossieGerado;
  criadoEm: string;
  expiraEm: string | null;
  documentos: number;
  falhas: number;
  listados: number;
  tamanhoBytes: number | null;
  entregaPorTicket: boolean;
  temArquivo: boolean;
  arquivoGerado: boolean;
  geradoPor?: string;
  /** S6: só em FALHA, com o código e o texto para o usuário. */
  falha?: { codigo: string | null; mensagem: string | null };
}

/** Formato pedido ao endpoint de conteúdo (S3, docs/07). */
export type FormatoDoConteudo = 'pdf' | 'original';

/** Resposta do conteúdo: o arquivo, ou o aviso de que ele passa do limite do Blob (nada foi baixado). */
export type ConteudoRecebido =
  | {
      tipo: 'blob';
      blob: Blob;
      /** Formato de origem (cabeçalho X-Vistas-Convertido-De, ex.: XLSX), só quando houve conversão. */
      convertidoDe: string | null;
      nome: string | null;
    }
  | { tipo: 'grande'; megabytes: number };

/** Estados em que o polling para: o servidor não muda o pedido sem uma ação do usuário. */
export const STATUS_FINAIS: readonly StatusDossie[] = ['CONCLUIDO', 'PENDENTE_DECISAO', 'CANCELADO', 'EXPIRADO', 'FALHA'];
