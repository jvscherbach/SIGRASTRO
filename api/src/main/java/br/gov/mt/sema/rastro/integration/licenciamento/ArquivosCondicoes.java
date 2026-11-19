package br.gov.mt.sema.rastro.integration.licenciamento;

/**
 * Condições de ativação da API de arquivos. Como no SIGA, URL ausente ou vazia (LICENCIAMENTO_API_URL= no .env.local)
 * significa "não configurada"; por isso a checagem é por expressão e não por ConditionalOnProperty.
 */
final class ArquivosCondicoes {

    static final String CONFIGURADO = "!'${rastro.arquivos.url:}'.isBlank()";
    static final String NAO_CONFIGURADO = "'${rastro.arquivos.url:}'.isBlank()";

    private ArquivosCondicoes() {
    }
}
