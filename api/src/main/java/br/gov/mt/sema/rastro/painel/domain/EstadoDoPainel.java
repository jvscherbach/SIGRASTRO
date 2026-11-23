package br.gov.mt.sema.rastro.painel.domain;

/** Estado do instantâneo do painel (S10). */
public enum EstadoDoPainel {
    /** Ainda não houve a primeira leitura. */
    PREPARANDO,
    /** A última leitura deu certo. */
    PRONTO,
    /** A última tentativa falhou; vale o último instantâneo bom, com a data dele. */
    DESATUALIZADO,
    /** Nunca houve leitura boa. */
    INDISPONIVEL
}
