package br.gov.mt.sema.rastro.financeiro.domain;

/**
 * Combinação que a regra de validade não espera (S9, bloco 2). O lançamento é <b>mantido</b>, como está, com o aviso
 * {@link CodigoDoAvisoFinanceiro#LANCAMENTO_INCONSISTENTE}; nunca descartado em silêncio nem "corrigido".
 */
public enum Inconsistencia {
    /** {@code STATUS} nulo ou fora do enum ({@link StatusDaGuia}). */
    STATUS_DESCONHECIDO,
    /** {@code AGUARDANDO_PAGAMENTO}, {@code PAGA} ou {@code VENCIDA} sem {@code NUM_DAR} (fora da regra de validade). */
    SEM_DAR,
    /** {@code PAGA} sem {@code DATA_PAGAMENTO}. */
    PAGA_SEM_DATA_DE_PAGAMENTO
}
