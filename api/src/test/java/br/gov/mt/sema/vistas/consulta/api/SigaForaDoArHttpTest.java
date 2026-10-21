package br.gov.mt.sema.vistas.consulta.api;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** A aplicação sobe sem o SIGA (não configurado ou inacessível): health UP e consultas 503. */
class SigaForaDoArHttpTest {

    abstract static class Base {
        @Autowired
        MockMvc mvc;

        @Autowired
        ObjectMapper json;

        String token() throws Exception {
            String corpo = json.writeValueAsString(Map.of("usuario", "gestor.demo", "senha", "senha-de-teste"));
            String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andReturn().getResponse().getContentAsString();
            return json.readTree(resposta).get("token").asText();
        }

        void verificarSaudeEBuscaIndisponivel() throws Exception {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));

            mvc.perform(get("/api/busca?tipo=REQUERIMENTO&numero=1/2025").header("Authorization", "Bearer " + token()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("SIGA_INDISPONIVEL"))
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.correlationId", not(emptyOrNullString())))
                    .andExpect(header().exists("X-Correlation-Id"));

            mvc.perform(get("/api/requerimentos/1").header("Authorization", "Bearer " + token()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("SIGA_INDISPONIVEL"));
        }
    }

    @Nested
    @SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url="})
    @AutoConfigureMockMvc
    class UrlVazia extends Base {
        @Test
        void aplicacaoSobeELeituraResponde503() throws Exception {
            verificarSaudeEBuscaIndisponivel();
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "vistas.demo.senha=senha-de-teste",
            "vistas.datasource.siga.url=jdbc:oracle:thin:@//127.0.0.1:1/INEXISTENTE",
            "vistas.datasource.siga.username=x",
            "vistas.datasource.siga.password=x",
            "vistas.datasource.siga.hikari.connection-timeout=1000"})
    @AutoConfigureMockMvc
    class UrlInacessivel extends Base {
        @Test
        void aplicacaoSobeComSigaForaDoArELeituraResponde503() throws Exception {
            verificarSaudeEBuscaIndisponivel();
        }
    }
}
