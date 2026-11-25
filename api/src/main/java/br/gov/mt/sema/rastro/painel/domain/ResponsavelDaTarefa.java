package br.gov.mt.sema.rastro.painel.domain;

/**
 * Com quem está uma tarefa ativa, como o SIGA resolveu em lote (mesma regra do {@code tarefa-atual.sql}). O CPF do
 * {@code ASSIGNEE_} nunca chega aqui: só a categoria e, para {@link #SERVIDOR}, o nome.
 */
public enum ResponsavelDaTarefa {
    /** Sem {@code ASSIGNEE_}: tarefa de grupo (área) ou sem atribuição. */
    NAO_ATRIBUIDA,
    /** {@code ASSIGNEE_} é o CPF do interessado do requerimento ("Interessado (cadastrante)"). */
    INTERESSADO,
    /** {@code ASSIGNEE_} com nome resolvido em {@code SERVIDOR} ou, se não houver, em {@code PESSOA}. */
    SERVIDOR,
    /** {@code ASSIGNEE_} preenchido sem nome resolvido (ou que não é um CPF de 11 dígitos). */
    NAO_IDENTIFICADO
}
