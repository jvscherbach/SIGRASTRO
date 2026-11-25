package br.gov.mt.sema.rastro.painel.domain;

import java.util.Objects;

/** Aviso do painel: o que não é certo aparece como nota, nunca como número afirmado. Sem dado pessoal. */
public record AvisoDoPainel(String codigo, String texto) {

    public static final String INCONSISTENCIA_NA_SERIE = "INCONSISTENCIA_NA_SERIE";
    public static final String SOMA_DAS_BARRAS_MAIOR_QUE_O_TOTAL = "SOMA_DAS_BARRAS_MAIOR_QUE_O_TOTAL";
    public static final String NOMES_OCULTOS = "NOMES_OCULTOS";
    public static final String TRUNCADO = "TRUNCADO";
    public static final String LEITURA_PARCIAL = "LEITURA_PARCIAL";

    public AvisoDoPainel {
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(texto);
    }
}
