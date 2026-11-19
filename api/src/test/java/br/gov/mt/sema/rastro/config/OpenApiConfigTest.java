package br.gov.mt.sema.rastro.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

/** R1: a documentação da API se apresenta como Rastro. O OpenAPI fica desligado por padrão (OPENAPI_ENABLED). */
class OpenApiConfigTest {

    @Test
    void tituloEDescricaoUsamONomeRastro() {
        OpenAPI api = new OpenApiConfig().rastroOpenApi();

        assertThat(api.getInfo().getTitle()).isEqualTo("Rastro API");
        assertThat(api.getInfo().getDescription()).startsWith("Rastro, complemento de consulta ao SIGA Licenciamento");
        assertThat(api.getInfo().getTitle() + api.getInfo().getDescription()).doesNotContainIgnoringCase("SIGA Vistas");
    }
}
