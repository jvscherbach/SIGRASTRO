package br.gov.mt.sema.rastro.documento.api.dto;

/** Arquivo que impediu o ZIP das geometrias: o nome que teria no ZIP e o motivo exibível (sem chave, URL nem token). */
public record GeometriaComProblemaResponse(String nome, String motivo) {
}
