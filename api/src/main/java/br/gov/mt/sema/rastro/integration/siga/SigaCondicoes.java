package br.gov.mt.sema.rastro.integration.siga;

/**
 * Condições de ativação do acesso ao SIGA. ConditionalOnProperty trataria uma URL vazia
 * (SIGA_DB_URL= no .env.local) como configurada; por isso a checagem é por expressão.
 */
final class SigaCondicoes {

    static final String CONFIGURADO = "!'${rastro.datasource.siga.url:}'.isBlank()";
    static final String NAO_CONFIGURADO = "'${rastro.datasource.siga.url:}'.isBlank()";

    private SigaCondicoes() {
    }
}
