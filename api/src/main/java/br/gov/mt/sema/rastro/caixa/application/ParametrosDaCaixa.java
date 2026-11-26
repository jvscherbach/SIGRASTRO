package br.gov.mt.sema.rastro.caixa.application;

/**
 * Parâmetros da caixa (S11): lidos a cada ciclo. Ligados em {@code config}. O teto de requerimentos e o tempo total são
 * os do painel (a caixa não cria parâmetro para eles); os outros dois são próprios.
 */
public interface ParametrosDaCaixa {

    /** {@code rastro.caixaAtualizacaoMinutos}: intervalo da atualização automática (2 a 120; padrão 10). */
    int atualizacaoMinutos();

    /** {@code rastro.caixaIntervaloMinimoManualSegundos}: intervalo mínimo entre atualizações manuais (15 a 3600; padrão 60). */
    int intervaloMinimoManualSegundos();

    /** Teto de requerimentos por atualização (o do painel). */
    int limiteRequerimentos();

    /** Tempo total de uma atualização, em segundos (o do painel). */
    int timeoutSegundos();
}
