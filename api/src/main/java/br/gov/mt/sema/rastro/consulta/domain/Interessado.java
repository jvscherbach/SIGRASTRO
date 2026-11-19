package br.gov.mt.sema.rastro.consulta.domain;

/** Pessoa interessada; o requerimento pode não ter interessado cadastrado. */
public record Interessado(String nome, String cpfCnpj) {
}
