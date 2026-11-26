package br.gov.mt.sema.rastro.painel.api;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import br.gov.mt.sema.rastro.painel.application.port.ResumoDoAnoNoSiga;
import br.gov.mt.sema.rastro.painel.application.port.TarefasAtivasEmLote;
import br.gov.mt.sema.rastro.painel.domain.BaseDoAno;
import br.gov.mt.sema.rastro.painel.domain.ContagemDoMes;
import br.gov.mt.sema.rastro.painel.domain.LeituraDaSerie;
import br.gov.mt.sema.rastro.painel.domain.LeituraDasTarefas;
import br.gov.mt.sema.rastro.painel.domain.RequerimentoDaBase;
import br.gov.mt.sema.rastro.painel.domain.ResponsavelDaTarefa;
import br.gov.mt.sema.rastro.painel.domain.TarefaAtiva;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Lista de requerimentos por analista do painel (S11, bloco 9), pelo contexto completo e as duas portas de leitura por
 * dublê: a soma da lista é a da barra, os nomes ocultos, o 403 antes de qualquer leitura e nenhum CPF. Dados fictícios.
 */
@SpringBootTest(properties = {"rastro.datasource.siga.url=", "rastro.painel.ano=2026"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PainelAnalistasHttpTest {

    private static final String SERVIDOR_1 = "Servidor Exemplo 01";
    private static final String SERVIDOR_2 = "Servidor Exemplo 02";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CatalogoDeEtapas catalogo;

    @MockitoBean
    ResumoDoAnoNoSiga resumo;

    @MockitoBean
    TarefasAtivasEmLote tarefas;

    @BeforeEach
    void preparar() {
        reset(resumo, tarefas);
        ModeloDeFluxo modelo = catalogo.modelos().get(0);
        String instancia = modelo.instancias().get(0);
        List<Etapa> etapas = modelo.etapas().stream().filter(e -> !e.idsTecnicos().isEmpty()).toList();
        String primeira = etapas.get(0).idsTecnicos().get(0);
        String segunda = etapas.get(1).idsTecnicos().get(0);

        when(resumo.lerSerie(any(), any())).thenReturn(new LeituraDaSerie(
                List.of(ContagemDoMes.de(YearMonth.of(2026, 1), 5)), List.of(), 0));
        List<RequerimentoDaBase> base = new ArrayList<>();
        base.add(req(1, instancia, "Interessado Exemplo LTDA"));
        base.add(req(2, instancia, "Interessada Exemplo Açaí"));
        base.add(req(3, instancia, "Interessado Exemplo 03"));
        base.add(req(4, instancia, "Interessado Exemplo 04"));
        base.add(new RequerimentoDaBase(5, Optional.of("TIPO_FORA_DO_CATALOGO"), Optional.of("EM_ANALISE")));
        when(resumo.lerBase(any(), anyInt(), any())).thenReturn(new BaseDoAno(base, false));
        when(tarefas.ler(anyList(), any(), any())).thenReturn(new LeituraDasTarefas(List.of(
                new TarefaAtiva(1, modelo.chave(), primeira, ResponsavelDaTarefa.SERVIDOR, Optional.of(SERVIDOR_1), List.of()),
                new TarefaAtiva(2, modelo.chave(), segunda, ResponsavelDaTarefa.SERVIDOR, Optional.of(SERVIDOR_1), List.of()),
                new TarefaAtiva(3, modelo.chave(), primeira, ResponsavelDaTarefa.SERVIDOR, Optional.of(SERVIDOR_2), List.of()),
                new TarefaAtiva(4, modelo.chave(), primeira, ResponsavelDaTarefa.NAO_ATRIBUIDA, Optional.empty(), List.of())),
                Set.of(1L, 2L, 3L, 4L), false));
    }

    private static RequerimentoDaBase req(long id, String instancia, String interessado) {
        return new RequerimentoDaBase(id, Optional.of(instancia), Optional.of("EM_ANALISE"), Optional.of("REQ-" + id),
                Optional.of(interessado), Optional.of(LocalDate.of(2026, 2, 10)));
    }

    private String token(String usuario) throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", usuario, "senha", UsuariosDeTeste.senhaDe(usuario)));
        return "Bearer " + json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo)).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions pedir(String usuario, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header("Authorization", token(usuario)));
    }

    private static String texto(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode analistasProntos(String usuario) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JsonNode atual;
        do {
            atual = json.readTree(texto(pedir(usuario, get("/api/painel/analistas"))));
            if ("PRONTO".equals(atual.get("estado").asText())) {
                return atual;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < limite);
        throw new AssertionError("o painel não ficou PRONTO: " + atual.get("estado"));
    }

    private static String analistaDe(JsonNode lista, String rotulo) {
        for (JsonNode a : lista.get("analistas")) {
            if (rotulo.equals(a.get("rotulo").asText())) {
                return a.get("analista").asText();
            }
        }
        throw new AssertionError("analista ausente: " + rotulo);
    }

    @Test
    void operadorEAnalistaEmSessaoOu401NaoLeemNadaEOForbiddenVemAntesDoBadRequest() throws Exception {
        assertThat(pedir("operador", get("/api/painel/analistas")).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        String corpo = texto(pedir("operador", get("/api/painel/analistas/abc/requerimentos")));
        assertThat(corpo).contains("PERFIL_SEM_PERMISSAO");
        // 403 antes de validar o filtro (busca enorme)
        assertThat(pedir("operador", get("/api/painel/analistas/abc/requerimentos").param("busca", "x".repeat(500)))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/painel/analistas")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/painel/analistas/abc/requerimentos")).andReturn().getResponse().getStatus())
                .isEqualTo(401);

        verify(resumo, never()).lerSerie(any(), any());
        verify(resumo, never()).lerBase(any(), anyInt(), any());
    }

    @Test
    void listaDeAnalistasBateComAsBarrasERequerimentosDoAnalistaTemFiltros() throws Exception {
        JsonNode painel = null;
        JsonNode analistas = analistasProntos("gestor");
        painel = json.readTree(texto(pedir("gestor", get("/api/painel"))));

        // os totais são os das barras da carga por responsável
        for (JsonNode barra : painel.get("cargaPorResponsavel")) {
            JsonNode linha = null;
            for (JsonNode a : analistas.get("analistas")) {
                if (a.get("rotulo").asText().equals(barra.get("rotulo").asText())) {
                    linha = a;
                }
            }
            assertThat(linha).as(barra.get("rotulo").asText()).isNotNull();
            assertThat(linha.get("total").asLong()).isEqualTo(barra.get("total").asLong());
        }
        assertThat(analistas.get("analistas")).hasSize(painel.get("cargaPorResponsavel").size());
        assertThat(analistas.get("etapas")).isNotEmpty();
        assertThat(analistas.get("nomesOcultos").asBoolean()).isFalse();

        String chave1 = analistaDe(analistas, SERVIDOR_1);
        JsonNode todos = json.readTree(texto(pedir("admin", get("/api/painel/analistas/" + chave1 + "/requerimentos"))));
        assertThat(todos.get("total").asLong()).isEqualTo(2);
        assertThat(todos.get("quantidade").asInt()).isEqualTo(2);
        assertThat(todos.get("requerimentos")).hasSize(2);
        assertThat(todos.get("requerimentos").get(0).get("numero").asText()).isEqualTo("REQ-1");
        assertThat(todos.get("requerimentos").get(0).get("interessado").asText()).isEqualTo("Interessado Exemplo LTDA");
        assertThat(todos.get("requerimentos").get(0).get("protocolo").asText()).isEqualTo("2026-02-10");
        assertThat(todos.get("requerimentos").get(0).get("etapas")).isNotEmpty();

        // busca sem acento e sem diferenciar maiúsculas
        JsonNode busca = json.readTree(texto(pedir("gestor",
                get("/api/painel/analistas/" + chave1 + "/requerimentos").param("busca", "acai"))));
        assertThat(busca.get("quantidade").asInt()).isEqualTo(1);
        assertThat(busca.get("total").asLong()).isEqualTo(2);

        // filtro por etapa (rótulo exato, o da lista de etapas e da primeira requisição)
        String etapa = todos.get("requerimentos").get(0).get("etapas").get(0).asText();
        JsonNode porEtapa = json.readTree(texto(pedir("gestor",
                get("/api/painel/analistas/" + chave1 + "/requerimentos").param("etapa", etapa))));
        assertThat(porEtapa.get("quantidade").asInt()).isEqualTo(1);

        // Fora do catálogo nunca vira analista
        assertThat(analistas.toString()).doesNotContain("Interessado Exemplo");
    }

    @Test
    void chaveDesconhecidaE404ComentarioSemDetalheInternoEBuscaGrandeE400() throws Exception {
        analistasProntos("gestor");

        var inexistente = pedir("gestor", get("/api/painel/analistas/0000000000000000/requerimentos"))
                .andReturn().getResponse();
        assertThat(inexistente.getStatus()).isEqualTo(404);
        assertThat(json.readTree(inexistente.getContentAsString(StandardCharsets.UTF_8)).get("errorCode").asText())
                .isEqualTo("NAO_ENCONTRADO");
        assertThat(pedir("gestor", get("/api/painel/analistas/abc/requerimentos").param("busca", "x".repeat(101)))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void nomesOcultosNaoRevelamNomeNemDeixamAlcancarUmServidor() throws Exception {
        JsonNode comNomes = analistasProntos("gestor");
        String chaveDoServidor = analistaDe(comNomes, SERVIDOR_1);
        String admin = token("admin");
        assertThat(mvc.perform(put("/api/configuracoes/parametros").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alteracoes\":{\"rastro.mostrarNomeResponsavel\":false}}")).andReturn().getResponse()
                .getStatus()).isEqualTo(200);
        try {
            JsonNode oculto = json.readTree(texto(pedir("gestor", get("/api/painel/analistas"))));
            assertThat(oculto.get("nomesOcultos").asBoolean()).isTrue();
            assertThat(oculto.toString()).doesNotContain("Servidor Exemplo");
            assertThat(oculto.get("analistas").findValuesAsText("tipo")).doesNotContain("SERVIDOR", "OUTROS");
            assertThat(oculto.get("avisos").findValuesAsText("codigo")).contains("NOMES_OCULTOS");

            // a chave de um servidor (obtida antes) já não leva a lugar nenhum
            assertThat(pedir("gestor", get("/api/painel/analistas/" + chaveDoServidor + "/requerimentos"))
                    .andReturn().getResponse().getStatus()).isEqualTo(404);
            // a categoria sem nome segue com lista, sem nome de servidor
            String semNome = oculto.get("analistas").get(0).get("analista").asText();
            String lista = texto(pedir("gestor", get("/api/painel/analistas/" + semNome + "/requerimentos")));
            assertThat(lista).doesNotContain("Servidor Exemplo");
        } finally {
            mvc.perform(delete("/api/configuracoes/parametros/rastro.mostrarNomeResponsavel").header("Authorization", admin));
        }
    }

    @Test
    void nenhumCpfNemIdTecnicoNasRespostas() throws Exception {
        JsonNode analistas = analistasProntos("gestor");
        String corpo = analistas.toString() + texto(pedir("gestor", get("/api/painel/analistas/"
                + analistaDe(analistas, SERVIDOR_1) + "/requerimentos")));

        assertThat(corpo).doesNotContainPattern("(?<![0-9])\\d{11}(?![0-9])")
                .doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}")
                .doesNotContainPattern("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}")
                .doesNotContain("idDaTarefa").doesNotContain("taskAnalisar");
    }
}
