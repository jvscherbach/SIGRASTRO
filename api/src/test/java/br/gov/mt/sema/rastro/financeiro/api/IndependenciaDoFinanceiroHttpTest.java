package br.gov.mt.sema.rastro.financeiro.api;

import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.quandoLer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.consulta.application.ObjetoEPrioridadeDoRequerimento;
import br.gov.mt.sema.rastro.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.rastro.consulta.application.TarefaAtualDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.domain.Lancamento;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import br.gov.mt.sema.rastro.tramitacao.application.ConsultarPosicaoAtual;
import br.gov.mt.sema.rastro.tramitacao.application.MontarTramitacao;
import br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste;
import br.gov.mt.sema.rastro.tramitacao.application.TramitacoesDeTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Resiliência e independência do financeiro (S9, bloco 7), pela API real com dublês das portas: banco lento (tempo limite
 * estourado) ou fora → {@code INDISPONIVEL} (200), nunca 5xx; o financeiro fora não muda a posição, a tramitação nem os
 * documentos; e o inverso: Camunda fora, tramitação quebrada ou documentos fora não mudam o financeiro.
 */
@SpringBootTest(properties = {"rastro.demo.senha=senha-de-teste", "rastro.datasource.siga.url=",
        "rastro.tramitacao.tramitacao-cache-segundos=0", "rastro.tramitacao.posicao-cache-segundos=0"})
@AutoConfigureMockMvc
class IndependenciaDoFinanceiroHttpTest {

    private static final long ID = 9001;
    private static final LocalDateTime EMISSAO = LocalDateTime.of(2026, 3, 4, 9, 0);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    RequerimentosDoSiga siga;

    @MockitoBean
    TarefaAtualDoRequerimento tarefas;

    @MockitoBean
    ObjetoEPrioridadeDoRequerimento objetoEPrioridade;

    @MockitoBean
    DocumentosDoRequerimento documentos;

    @MockitoBean
    ConsultarPosicaoAtual consultar;

    @MockitoBean
    MontarTramitacao montar;

    @MockitoBean
    RegistrarEvento registrar;

    @MockitoBean
    LancamentosDoRequerimento lancamentos;

    @BeforeEach
    void preparar() {
        reset(siga, documentos, consultar, montar, registrar, lancamentos);
        Requerimento r = new Requerimento(ID, ID + "/2026", "9901/2026", "Requerimento de Outorga",
                Situacao.de("EM_ANALISE"), EMISSAO, null, new Interessado("Interessado Fictício S9", "12.345.678/0001-90"),
                "Outorga (FICTÍCIO)");
        when(siga.buscarPorId(ID)).thenReturn(Optional.of(r));
        when(siga.buscarPorCriterios(any())).thenReturn(new br.gov.mt.sema.rastro.consulta.domain.Pagina<>(
                List.of(r.resumo()), 1, 0, 20));
        quandoLer(consultar, ID, PosicoesDeTeste.ativaComServidor());
        when(montar.montar(ID)).thenReturn(TramitacoesDeTeste.ativaComVoltas());
        when(documentos.listar(ID)).thenReturn(List.of());
        LeituraDosLancamentos paga = new LeituraDosLancamentos(List.of(new Lancamento(1, ID, "PAGA", false, "P",
                "FICT-0001", EMISSAO, EMISSAO.plusDays(10), EMISSAO.plusDays(3), null, new BigDecimal("1250.50"),
                BigDecimal.ZERO, new BigDecimal("35.7286"), null)), false);
        when(lancamentos.lerDoRequerimento(anyLong())).thenReturn(paga);
        when(lancamentos.lerEmLote(anyCollection())).thenReturn(Map.of(ID, paga));
    }

    private String token() throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", "gestor.demo", "senha", "senha-de-teste"));
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private MockHttpServletResponse obter(String caminho) throws Exception {
        return mvc.perform(get(caminho).header("Authorization", "Bearer " + token())).andReturn().getResponse();
    }

    private static String corpo(MockHttpServletResponse r) throws Exception {
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    // --- banco lento ou fora: INDISPONIVEL, nunca 5xx

    @Test
    void banco_lento_tempo_limite_estourado_e_indisponivel_sem_5xx() throws Exception {
        when(lancamentos.lerDoRequerimento(anyLong())).thenThrow(new QueryTimeoutException("tempo limite"));
        when(lancamentos.lerEmLote(anyCollection())).thenThrow(new QueryTimeoutException("tempo limite"));
        for (String caminho : List.of("/api/requerimentos/" + ID + "/financeiro", "/api/processos/9901-2026/financeiro")) {
            MockHttpServletResponse r = obter(caminho);
            assertThat(r.getStatus()).as(caminho).isEqualTo(200);
            assertThat(corpo(r)).as(caminho).contains("\"estado\":\"INDISPONIVEL\"").doesNotContain("tempo limite");
        }
    }

    @Test
    void banco_fora_e_indisponivel_sem_5xx() throws Exception {
        when(lancamentos.lerDoRequerimento(anyLong())).thenThrow(new DataAccessResourceFailureException("fora"));
        MockHttpServletResponse r = obter("/api/requerimentos/" + ID + "/financeiro");
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(json.readTree(corpo(r)).get("estado").asText()).isEqualTo("INDISPONIVEL");
    }

    // --- financeiro fora não muda tramitação nem documentos

    @Test
    void financeiro_fora_nao_muda_posicao_tramitacao_nem_documentos() throws Exception {
        List<String> rotas = List.of("/api/requerimentos/" + ID + "/posicao", "/api/requerimentos/" + ID + "/tramitacao",
                "/api/requerimentos/" + ID + "/documentos");
        Map<String, JsonNode> comFinanceiro = new java.util.HashMap<>();
        for (String rota : rotas) {
            MockHttpServletResponse r = obter(rota);
            assertThat(r.getStatus()).as(rota).isEqualTo(200);
            comFinanceiro.put(rota, json.readTree(corpo(r)));
        }
        when(lancamentos.lerDoRequerimento(anyLong())).thenThrow(new DataAccessResourceFailureException("fora"));
        when(lancamentos.lerEmLote(anyCollection())).thenThrow(new DataAccessResourceFailureException("fora"));
        for (String rota : rotas) {
            MockHttpServletResponse r = obter(rota);
            assertThat(r.getStatus()).as(rota + " com o financeiro fora").isEqualTo(200);
            JsonNode sem = json.readTree(corpo(r));
            if (rota.endsWith("/tramitacao")) {
                // Só as marcas financeiras podem sumir; o caminho, os avisos e o resto ficam iguais.
                assertThat(semMarcas(sem)).as(rota).isEqualTo(semMarcas(comFinanceiro.get(rota)));
            } else {
                assertThat(sem).as(rota).isEqualTo(comFinanceiro.get(rota));
            }
        }
    }

    // --- e o inverso: Camunda, tramitação e documentos fora não mudam o financeiro

    @Test
    void camunda_tramitacao_e_documentos_fora_nao_mudam_o_financeiro() throws Exception {
        JsonNode antes = json.readTree(corpo(obter("/api/requerimentos/" + ID + "/financeiro")));
        quandoLer(consultar, ID, PosicoesDeTeste.indisponivel());
        when(montar.montar(ID)).thenThrow(new IllegalStateException("Camunda fora"));
        when(documentos.listar(ID)).thenThrow(new IllegalStateException("documentos fora"));
        MockHttpServletResponse r = obter("/api/requerimentos/" + ID + "/financeiro");
        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode depois = json.readTree(corpo(r));
        assertThat(depois.get("estado").asText()).isEqualTo("ATIVO");
        assertThat(depois.get("resumo").asText()).isEqualTo("QUITADO");
        assertThat(depois).isEqualTo(antes);
        assertThat(obter("/api/requerimentos/" + ID + "/tramitacao").getStatus()).isEqualTo(200);
    }

    private static JsonNode semMarcas(JsonNode tramitacao) {
        JsonNode copia = tramitacao.deepCopy();
        copia.get("passagens").forEach(p -> ((com.fasterxml.jackson.databind.node.ObjectNode) p).remove("marcasFinanceiras"));
        return copia;
    }
}
