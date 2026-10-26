package br.gov.mt.sema.vistas.dossie.domain;

/**
 * Documento que interrompeu a geração e o motivo (RN09). {@code codigo} só é preenchido quando o motivo é a recusa
 * da credencial da API de arquivos, que vale para todos os documentos.
 */
public record ErroDoDossie(String origem, String id, String titulo, String motivo, String codigo) {

    public static final String CREDENCIAL_ARQUIVOS_RECUSADA = "CREDENCIAL_ARQUIVOS_RECUSADA";
}
