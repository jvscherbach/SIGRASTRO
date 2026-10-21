package br.gov.mt.sema.vistas.integration.siga;

/**
 * Condições de ativação do acesso ao SIGA. ConditionalOnProperty trataria uma URL vazia
 * (SIGA_DB_URL= no .env.local) como configurada; por isso a checagem é por expressão.
 */
final class SigaCondicoes {

    static final String CONFIGURADO = "!'${vistas.datasource.siga.url:}'.isBlank()";
    static final String NAO_CONFIGURADO = "'${vistas.datasource.siga.url:}'.isBlank()";

    private SigaCondicoes() {
    }
}
