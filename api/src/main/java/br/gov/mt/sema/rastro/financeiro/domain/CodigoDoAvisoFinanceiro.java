package br.gov.mt.sema.rastro.financeiro.domain;

/** Códigos estáveis dos avisos do financeiro (S9). O aviso de taxa vencida na posição é do bloco 5. */
public enum CodigoDoAvisoFinanceiro {
    /** Há lançamento mantido com {@link Inconsistencia}. */
    LANCAMENTO_INCONSISTENTE,
    /** A leitura passou do limite de lançamentos por requerimento; os mais recentes ficaram de fora. */
    LANCAMENTOS_ALEM_DO_LIMITE
}
