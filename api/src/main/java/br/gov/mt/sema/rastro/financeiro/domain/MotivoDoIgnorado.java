package br.gov.mt.sema.rastro.financeiro.domain;

/**
 * Por que um lançamento foi ignorado pela regra de validade (S9, bloco 2). A contagem por motivo aparece só no diagnóstico
 * do Administrador (bloco 10); nenhum lançamento ignorado entra na lista nem no resumo.
 */
public enum MotivoDoIgnorado {
    /** {@code CANCELADA} sem {@code NUM_DAR}: registro criado e descartado pelo próprio sistema. */
    FALSO_SEM_DAR,
    /** {@code AGUARDANDO_EMISSAO} com {@code TAXA_ISENTA = 1}: ruído, ignorado por ora (docs/10). */
    RUIDO_ISENTO_AGUARDANDO
}
