package br.gov.mt.sema.vistas.consulta.domain;

/** Pessoa interessada; o requerimento pode não ter interessado cadastrado. */
public record Interessado(String nome, String cpfCnpj) {
}
