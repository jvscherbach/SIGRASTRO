package br.gov.mt.sema.rastro.documento.domain;

/** Tipo do arquivo pelo conteúdo (assinatura), não pelo nome. Orienta o pipeline: PDF segue, convertíveis vão ao conversor. */
public enum TipoReal {
    PDF,
    PLANILHA,
    TEXTO,
    IMAGEM,
    /** ZIP que não é documento Office/ODF (RN11). */
    COMPACTADO,
    DESCONHECIDO
}
