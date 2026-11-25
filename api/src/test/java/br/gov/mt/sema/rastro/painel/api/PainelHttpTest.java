package br.gov.mt.sema.rastro.painel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Contrato HTTP do painel (S10, bloco 5), com o painel de verdade (contexto Spring completo) e as duas portas de leitura
 * por dublê: perfis, estados, o 429, a auditoria e o mascaramento dos nomes. Dados fictícios; ano fixo em 2026.
 */
@SpringBootTest(properties = {"rastro.demo.senha=senha-de-teste", "rastro.datasource.siga.url=", "rastro.painel.ano=2026"})
@AutoConfigureMockMvc
class PainelHttpTest {

    private static final String NOME_FICTICIO = "Servidor Exemplo 01";

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

    @MockitoBean
    RegistrarEvento registrar;

    private final CountDownLatch liberar = new CountDownLatch(1);

    @BeforeEach
    void preparar() {
        reset(resumo, tarefas, registrar);
        ModeloDeFluxo modelo = catalogo.modelos().get(0);
        String instancia = modelo.instancias().get(0);
        Etapa etapa = modelo.etapas().stream().filter(e -> !e.idsTecnicos().isEmpty()).findFirst().orElseThrow();
        String idDaTarefa = etapa.idsTecnicos().get(0);

        when(resumo.lerSerie(any(), any())).thenAnswer(c -> {
            liberar.await(10, TimeUnit.SECONDS);
            return new LeituraDaSerie(List.of(ContagemDoMes.de(YearMonth.of(2026, 1), 5),
                    ContagemDoMes.de(YearMonth.of(2026, 2), 3)), List.of(ContagemDoMes.de(YearMonth.of(2026, 2), 2)), 1);
        });
        when(resumo.lerBase(any(), anyInt(), any())).thenReturn(new BaseDoAno(List.of(
                new RequerimentoDaBase(1, Optional.of(instancia), Optional.of("EM_ANDAMENTO")),
                new RequerimentoDaBase(2, Optional.of(instancia), Optional.of("EM_ANDAMENTO")),
                new RequerimentoDaBase(3, Optional.of(instancia), Optional.of("EM_ANDAMENTO"))), false));
        when(tarefas.ler(anyList(), any(), any())).thenReturn(new LeituraDasTarefas(List.of(
                new TarefaAtiva(1, modelo.chave(), idDaTarefa, ResponsavelDaTarefa.SERVIDOR,
                        Optional.of(NOME_FICTICIO), List.of()),
                new TarefaAtiva(2, modelo.chave(), idDaTarefa, ResponsavelDaTarefa.SERVIDOR,
                        Optional.of(NOME_FICTICIO), List.of()),
                new TarefaAtiva(3, modelo.chave(), idDaTarefa, ResponsavelDaTarefa.NAO_ATRIBUIDA,
                        Optional.empty(), List.of())), Set.of(1L, 2L, 3L), false));
    }

    private String token(String usuario) throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", usuario, "senha", "senha-de-teste"));
        return "Bearer " + json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo)).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions pedir(String usuario, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        return mvc.perform(req.header("Authorization", token(usuario)));
    }

    private static String texto(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<NovoEvento> eventosDoPainel() {
        ArgumentCaptor<NovoEvento> evento = ArgumentCaptor.forClass(NovoEvento.class);
        verify(registrar, atLeast(0)).registrar(evento.capture());
        return evento.getAllValues().stream().filter(e -> e.tipo() == TipoDeEvento.PAINEL).toList();
    }

    @Test
    void operadorESemLoginNaoVeemOPainelENadaEhLidoNemRegistrado() throws Exception {
        assertThat(pedir("operador.demo", get("/api/painel")).andReturn().getResponse().getStatus()).isEqualTo(403);
        String corpo = texto(pedir("operador.demo", post("/api/painel/atualizacao")));
        assertThat(corpo).contains("PERFIL_SEM_PERMISSAO");
        assertThat(mvc.perform(get("/api/painel")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/api/painel/atualizacao")).andReturn().getResponse().getStatus()).isEqualTo(401);

        verify(resumo, never()).lerSerie(any(), any());
        verify(resumo, never()).lerBase(any(), anyInt(), any());
        assertThat(eventosDoPainel()).isEmpty();
    }

    @Test
    void gestorEAdministradorVeemOPainelDeImediatoAtualizamEOsNomesPodemSerOcultados() throws Exception {
        // Primeira requisição: responde na hora, ainda PREPARANDO (a leitura está presa no dublê), com o ano visível.
        JsonNode preparando = json.readTree(texto(pedir("gestor.demo", get("/api/painel"))));
        assertThat(preparando.get("estado").asText()).isEqualTo("PREPARANDO");
        assertThat(preparando.get("ano").asInt()).isEqualTo(2026);
        assertThat(preparando.get("serie")).isEmpty();

        // Atualização manual com uma leitura em curso: aceita, sem iniciar outra.
        assertThat(pedir("gestor.demo", post("/api/painel/atualizacao")).andReturn().getResponse().getStatus())
                .isEqualTo(202);

        liberar.countDown();
        JsonNode pronto = esperarPronto("admin.demo");

        assertThat(pronto.get("ano").asInt()).isEqualTo(2026);
        assertThat(pronto.get("serie")).hasSize(12);
        assertThat(pronto.get("serie").get(0).get("protocolados").asLong()).isEqualTo(5);
        assertThat(pronto.get("serie").get(1).get("protocolados").asLong()).isEqualTo(3);
        assertThat(pronto.get("serie").get(1).get("concluidos").asLong()).isEqualTo(2);
        assertThat(pronto.get("totalProtocolados").asLong()).isEqualTo(8);
        assertThat(pronto.get("semDataDeProtocolo").asLong()).isEqualTo(1);
        assertThat(pronto.get("filaPorEtapa").findValues("total").stream().mapToLong(JsonNode::asLong).sum()).isEqualTo(3);
        assertThat(pronto.get("geradoEm").asText()).endsWith("-04:00");
        assertThat(pronto.get("nomesOcultos").asBoolean()).isFalse();
        JsonNode carga = pronto.get("cargaPorResponsavel");
        assertThat(carga.findValuesAsText("rotulo")).contains(NOME_FICTICIO, "Não atribuída");
        // Nada de id técnico do Camunda, de CPF nem de chave.
        String corpo = pronto.toString();
        assertThat(corpo).doesNotContain("taskFicticia").doesNotContain("idDaTarefa").doesNotContain("chave")
                .doesNotContainPattern("(?<![0-9])\\d{11}(?![0-9])").doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");

        // A atualização acabou há pouco: 429 com o tempo que falta.
        var recusada = pedir("gestor.demo", post("/api/painel/atualizacao")).andReturn().getResponse();
        assertThat(recusada.getStatus()).isEqualTo(429);
        JsonNode problema = json.readTree(recusada.getContentAsString(StandardCharsets.UTF_8));
        assertThat(problema.get("errorCode").asText()).isEqualTo("ATUALIZACAO_RECENTE");
        assertThat(problema.get("tentarEmSegundos").asLong()).isBetween(1L, 120L);
        assertThat(recusada.getHeader("Retry-After")).isNotBlank();

        // Nomes ocultos por configuração valem já na próxima leitura (RN12): só categorias sem nome.
        String admin = token("admin.demo");
        assertThat(mvc.perform(put("/api/configuracoes/parametros").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alteracoes\":{\"rastro.mostrarNomeResponsavel\":false}}")).andReturn().getResponse()
                .getStatus()).isEqualTo(200);
        try {
            JsonNode oculto = json.readTree(texto(pedir("gestor.demo", get("/api/painel"))));
            assertThat(oculto.get("nomesOcultos").asBoolean()).isTrue();
            assertThat(oculto.toString()).doesNotContain("Servidor Exemplo");
            assertThat(oculto.get("cargaPorResponsavel").findValuesAsText("tipo")).doesNotContain("SERVIDOR", "OUTROS");
            assertThat(oculto.get("avisos").findValuesAsText("codigo")).contains("NOMES_OCULTOS");
        } finally {
            mvc.perform(delete("/api/configuracoes/parametros/rastro.mostrarNomeResponsavel")
                    .header("Authorization", admin));
        }

        // Auditoria: só o ano e o estado no detalhe, nunca nome nem requerimento; negado e recusado não registram.
        List<NovoEvento> eventos = eventosDoPainel();
        assertThat(eventos).isNotEmpty();
        assertThat(eventos).allSatisfy(e -> {
            assertThat(e.detalhe()).startsWith("painel ").contains("ano 2026").doesNotContain("Servidor");
            assertThat(e.idRequerimento()).isNull();
        });
        assertThat(eventos.stream().filter(e -> e.detalhe().startsWith("painel atualizacao manual"))).hasSize(1);
        assertThat(eventos.stream().filter(e -> e.detalhe().startsWith("painel consulta"))).hasSizeGreaterThanOrEqualTo(3);
    }

    private JsonNode esperarPronto(String usuario) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JsonNode atual;
        do {
            atual = json.readTree(texto(pedir(usuario, get("/api/painel"))));
            if ("PRONTO".equals(atual.get("estado").asText())) {
                return atual;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < limite);
        throw new AssertionError("o painel não ficou PRONTO: " + atual.get("estado"));
    }
}
