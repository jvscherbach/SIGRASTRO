package br.gov.mt.sema.rastro.financeiro.domain;

/**
 * Aviso do financeiro: código estável, texto curto ao usuário e gravidade (mesma forma do aviso da posição da S7). O texto
 * nunca traz id técnico de lançamento, DAR nem valor.
 */
public record AvisoFinanceiro(CodigoDoAvisoFinanceiro codigo, String texto, Gravidade gravidade) {

    public enum Gravidade { INFO, ATENCAO }
}
