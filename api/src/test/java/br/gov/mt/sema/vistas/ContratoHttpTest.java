package br.gov.mt.sema.vistas;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "vistas.demo.senha=senha-de-teste")
@AutoConfigureMockMvc
class ContratoHttpTest {

    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void loginValidoDevolveTokenEPerfil() throws Exception {
        login("operador.demo", "senha-de-teste")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.perfil").value("OPERADOR"))
                .andExpect(jsonPath("$.usuario.cpfCnpj").value("12.345.678/0001-90"))
                .andExpect(jsonPath("$.usuario.permissoes.restritoAoInteressado").value(true))
                .andExpect(jsonPath("$.usuario.permissoes.verResponsavelTarefa").value(false))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void loginComSenhaErradaDevolve401EmProblemDetail() throws Exception {
        login("operador.demo", "errada")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("NAO_AUTENTICADO"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(".*-04:00$")))
                .andExpect(jsonPath("$.correlationId", not(emptyOrNullString())));
    }

    @Test
    void loginComUsuarioInexistenteDevolve401() throws Exception {
        login("ninguem", "senha-de-teste")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NAO_AUTENTICADO"));
    }

    @Test
    void loginSemCamposDevolve400ComCamposInvalidos() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.campos", hasSize(2)))
                .andExpect(jsonPath("$.correlationId", not(emptyOrNullString())));
    }

    @Test
    void corpoIlegivelDevolve400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{nao-e-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void meSemTokenDevolve401ComCorrelacaoGerada() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("NAO_AUTENTICADO"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_REGEX)))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void meComTokenInvalidoDevolve401() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer token-que-nao-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NAO_AUTENTICADO"));
    }

    @Test
    void meComTokenDevolveOUsuarioEEcoaOCorrelationIdRecebido() throws Exception {
        String token = tokenDe("admin.demo");

        mvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Correlation-Id", "teste-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "teste-123"))
                .andExpect(jsonPath("$.login").value("admin.demo"))
                .andExpect(jsonPath("$.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.permissoes.acessarConfiguracoes").value(true))
                .andExpect(jsonPath("$.permissoes.verResponsavelTarefa").value(true));
    }

    @Test
    void erroComCorrelationIdRecebidoDevolveOMesmoIdNoCorpo() throws Exception {
        mvc.perform(get("/api/auth/me").header("X-Correlation-Id", "meu-id-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "meu-id-1"))
                .andExpect(jsonPath("$.correlationId").value("meu-id-1"));
    }

    @Test
    void rotaInexistenteAutenticadaDevolve404() throws Exception {
        String token = tokenDe("gestor.demo");

        mvc.perform(get("/api/nao-existe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.path").value("/api/nao-existe"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void metodoNaoPermitidoDevolve405EmProblemDetail() throws Exception {
        String token = tokenDe("gestor.demo");

        mvc.perform(get("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void logoutInvalidaOToken() throws Exception {
        String token = tokenDe("operador.demo");

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEstaForaDoFiltroDeAutenticacao() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private ResultActions login(String usuario, String senha) throws Exception {
        String corpo = json.writeValueAsString(java.util.Map.of("usuario", usuario, "senha", senha));
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private String tokenDe(String usuario) throws Exception {
        String resposta = login(usuario, "senha-de-teste").andReturn().getResponse().getContentAsString();
        JsonNode no = json.readTree(resposta);
        return no.get("token").asText();
    }
}
