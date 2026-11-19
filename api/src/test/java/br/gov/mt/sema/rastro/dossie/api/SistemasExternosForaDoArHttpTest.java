package br.gov.mt.sema.rastro.dossie.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.consulta.application.ObjetoEPrioridadeDoRequerimento;
import br.gov.mt.sema.rastro.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.rastro.consulta.application.TarefaAtualDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.consulta.domain.TarefaAtual;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Com SIGA_DB_URL e LICENCIAMENTO_API_URL vazias a api sobe, o health é UP e as respostas são explícitas: a lista e a
 * criação do dossiê respondem 503; e, com o SIGA presente mas a API de arquivos ausente, o dossiê falha com o motivo
 * "API de arquivos não configurada" (e a listagem continua funcionando).
 */
class SistemasExternosForaDoArHttpTest {

    abstract static class Base {
        @Autowired
        MockMvc mvc;

        @Autowired
        ObjectMapper json;

        String bearer() throws Exception {
            String corpo = json.writeValueAsString(Map.of("usuario", "gestor.demo", "senha", "senha-de-teste"));
            String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andReturn().getResponse().getContentAsString();
            return "Bearer " + json.readTree(resposta).get("token").asText();
        }
    }

    @Nested
    @SpringBootTest(properties = {"rastro.demo.senha=senha-de-teste", "rastro.datasource.siga.url=", "rastro.arquivos.url=",
            "rastro.dossie.diretorio=${java.io.tmpdir}/vistas-teste-externos", "rastro.dossie.espaco-minimo-mb=0"})
    @AutoConfigureMockMvc
    class TudoVazio extends Base {
        @Test
        void healthUpEListaEDossieRespondem503() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));

            mvc.perform(get("/api/requerimentos/101/documentos").header("Authorization", bearer()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("SIGA_INDISPONIVEL"));

            mvc.perform(post("/api/dossies").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idRequerimento\":101,\"documentos\":[{\"origem\":\"COMPLEMENTACAO\",\"id\":\"1\"}]}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("SIGA_INDISPONIVEL"));
        }
    }

    @Nested
    @SpringBootTest(properties = {"rastro.demo.senha=senha-de-teste", "rastro.datasource.siga.url=", "rastro.arquivos.url=",
            "rastro.arquivos.token=", "rastro.dossie.diretorio=${java.io.tmpdir}/vistas-teste-externos", "rastro.dossie.espaco-minimo-mb=0"})
    @AutoConfigureMockMvc
    class SoAApiDeArquivosVazia extends Base {

        @MockitoBean
        RequerimentosDoSiga siga;

        @MockitoBean
        TarefaAtualDoRequerimento tarefas;

        @MockitoBean
        ObjetoEPrioridadeDoRequerimento objetoEPrioridade;

        @MockitoBean
        DocumentosDoRequerimento documentos;

        @Test
        void listaFuncionaEODossieFalhaComMotivoExplicito() throws Exception {
            when(tarefas.obter(anyLong())).thenReturn(TarefaAtual.semTarefa());
            when(objetoEPrioridade.objeto(anyLong())).thenReturn(ObjetoDoRequerimento.vazio());
            when(objetoEPrioridade.prioridade(anyLong())).thenReturn(PrioridadeAtendimento.de(List.of()));
            when(siga.buscarPorId(101)).thenReturn(Optional.of(new Requerimento(101, "3331/2025", "2825/2025", null,
                    Situacao.de("EM_ANALISE"), LocalDateTime.of(2025, 3, 31, 10, 12), null,
                    new Interessado("Associação Residencial Vale Verde", "12345678000190"), "Uso de Recursos Hídricos")));
            when(documentos.listar(101)).thenReturn(List.of(Documento.deLinha(OrigemDocumento.COMPLEMENTACAO, "1", 101, "K1",
                    "Ofício", "oficio.pdf", LocalDateTime.of(2025, 4, 1, 10, 0), null, 0)));
            String autorizacao = bearer();

            mvc.perform(get("/api/requerimentos/101/documentos").header("Authorization", autorizacao))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totais.total").value(1));

            String resposta = mvc.perform(post("/api/dossies").header("Authorization", autorizacao)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idRequerimento\":101,\"documentos\":[{\"origem\":\"COMPLEMENTACAO\",\"id\":\"1\"}]}"))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
            String id = json.readTree(resposta).get("id").asText();

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                String andamento = mvc.perform(get("/api/dossies/" + id).header("Authorization", autorizacao))
                        .andReturn().getResponse().getContentAsString();
                assertThat(json.readTree(andamento).get("status").asText()).isEqualTo("PENDENTE_DECISAO");
                assertThat(json.readTree(andamento).at("/falhas/0/motivo").asText()).isEqualTo("API de arquivos não configurada");
                assertThat(json.readTree(andamento).at("/falhas/0/id").asText()).isEqualTo("1");
            });
        }
    }
}
