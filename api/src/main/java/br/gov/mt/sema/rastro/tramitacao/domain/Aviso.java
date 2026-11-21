package br.gov.mt.sema.rastro.tramitacao.domain;

/**
 * Selo de aviso da posição: código estável, texto curto ao usuário (o selo mostra texto, não só cor ou ícone) e
 * gravidade. Nunca muda a posição: só a acompanha.
 */
public record Aviso(CodigoDoAviso codigo, String texto, Gravidade gravidade) {

    public enum Gravidade { INFO, ATENCAO }
}
