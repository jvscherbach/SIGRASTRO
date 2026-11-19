package br.gov.mt.sema.vistas.config;

import java.util.Arrays;
import java.util.List;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    private static final String PROBLEM_DETAIL = "ProblemDetail";

    @Bean
    OpenAPI vistasOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Token do login simulado do protótipo. Não é autenticação real."))
                .addSchemas(PROBLEM_DETAIL, problemDetailSchema())
                .addResponses("BadRequest", resposta("Parâmetros inválidos (PARAMETRO_INVALIDO)."))
                .addResponses("Unauthorized", resposta("Login ausente ou inválido (NAO_AUTENTICADO)."))
                .addResponses("Forbidden", resposta("Sem permissão para a função (PERFIL_SEM_PERMISSAO e correlatos)."))
                .addResponses("NotFound", resposta("Recurso inexistente (NAO_ENCONTRADO)."))
                .addResponses("InternalError", resposta("Erro interno (ERRO_INTERNO)."));

        return new OpenAPI()
                .info(new Info()
                        .title("Rastro API")
                        .version("0.1.0")
                        .description("Rastro, complemento de consulta ao SIGA Licenciamento (protótipo): consulta de requerimentos e processos e geração do dossiê em PDF (módulo Vistas)."))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    private static ApiResponse resposta(String descricao) {
        return new ApiResponse()
                .description(descricao)
                .content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL))));
    }

    private static Schema<?> problemDetailSchema() {
        List<String> codigos = Arrays.stream(ErrorCode.values()).map(Enum::name).toList();
        StringSchema errorCode = new StringSchema();
        errorCode.setEnum(codigos);

        ObjectSchema campo = new ObjectSchema();
        campo.addProperty("campo", new StringSchema());
        campo.addProperty("mensagem", new StringSchema());

        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("type", new StringSchema().example("about:blank"));
        schema.addProperty("title", new StringSchema());
        schema.addProperty("status", new IntegerSchema());
        schema.addProperty("detail", new StringSchema().description("Mensagem segura, sem detalhe técnico."));
        schema.addProperty("errorCode", errorCode);
        schema.addProperty("timestamp", new StringSchema().format("date-time").description("Fuso America/Cuiaba."));
        schema.addProperty("path", new StringSchema());
        schema.addProperty("correlationId", new StringSchema());
        schema.addProperty("campos", new ArraySchema().items(campo).description("Somente em erro de validação."));
        schema.required(List.of("status", "title", "errorCode", "timestamp", "path", "correlationId"));
        return schema;
    }
}
