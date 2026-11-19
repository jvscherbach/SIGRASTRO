package br.gov.mt.sema.rastro.dossie.domain;

/**
 * Motivo geral que deixou o pedido em PENDENTE_DECISAO sem ser de um documento só (S2.2): credencial recusada (com
 * {@code codigo}, vale para todos os documentos; origem, id e título são do documento em que a recusa ocorreu) ou falha
 * na montagem. As falhas por documento ficam nos itens. {@code codigo} só é preenchido quando o motivo é a recusa
 * da credencial da API de arquivos, que vale para todos os documentos.
 */
public record ErroDoDossie(String origem, String id, String titulo, String motivo, String codigo) {

    public static final String CREDENCIAL_ARQUIVOS_RECUSADA = "CREDENCIAL_ARQUIVOS_RECUSADA";
}
