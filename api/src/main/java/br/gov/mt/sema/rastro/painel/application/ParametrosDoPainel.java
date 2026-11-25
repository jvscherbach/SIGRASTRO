package br.gov.mt.sema.rastro.painel.application;

/**
 * Parâmetros do painel (S10, classe "imediato"): lidos a cada ciclo, então uma mudança vale na próxima atualização. Ligados
 * em {@code config}. Valores fora da faixa já chegam corrigidos para o padrão.
 */
public interface ParametrosDoPainel {

    /** {@code rastro.painelAno}: 0 = ano corrente (Cuiabá); senão 2000 a 2100. */
    int ano();

    /** {@code rastro.painelAtualizacaoMinutos}: intervalo da atualização automática (5 a 1440; padrão 30). */
    int atualizacaoMinutos();

    /** {@code rastro.painelLimiteRequerimentos}: teto de requerimentos por atualização (100 a 20000; padrão 3000). */
    int limiteRequerimentos();

    /** {@code rastro.painelTimeoutSegundos}: tempo total de uma atualização (10 a 300; padrão 60). */
    int timeoutSegundos();

    /** {@code rastro.painelIntervaloMinimoManualSegundos}: intervalo mínimo entre atualizações manuais (30 a 3600; padrão 120). */
    int intervaloMinimoManualSegundos();
}
