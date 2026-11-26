package br.gov.mt.sema.rastro.caixa.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.caixa.application.AtualizarCaixa;
import br.gov.mt.sema.rastro.caixa.application.port.PosseDoAnalistaEmLote;
import br.gov.mt.sema.rastro.caixa.domain.AnalistaParaLeitura;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.LeituraDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.PosseLida;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP da caixa do analista (S11, bloco 6), com o contexto Spring completo e a leitura da posse por dublê: o 403
 * por perfil antes de qualquer leitura, a caixa da própria sessão (nunca a de outro), os filtros, o aviso e o visto, o 429 da
 * atualização manual, a auditoria sem dado pessoal e a ausência de CPF e de chave em qualquer resposta. Dados fictícios; os
 * CPFs abaixo são de exemplo (válidos só no dígito, o cadastro exige) e jamais de uma pessoa.
 */
@SpringBootTest(properties = {"rastro.demo.senha=senha-de-teste", "rastro.datasource.siga.url=",
        "rastro.caixa.intervalo-minimo-manual-segundos=3600"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CaixaHttpTest {

    // Gerados por cálculo (o cadastro confere o dígito); nenhum literal de CPF no repositório.
    private static final String CPF_A_DIGITOS = cpfDeExemplo("111222333");
    private static final String CPF_A = mascarar(CPF_A_DIGITOS);
    private static final String CPF_B_DIGITOS = cpfDeExemplo("444555666");
    private static final String CPF_B = mascarar(CPF_B_DIGITOS);
    private static final String USUARIOS = "/api/configuracoes/usuarios";

    private static String cpfDeExemplo(String base) {
        String digitos = base;
        for (int posicao = 9; posicao <= 10; posicao++) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) {
                soma += (digitos.charAt(i) - '0') * (posicao + 1 - i);
            }
            int resto = (soma * 10) % 11;
            digitos += resto == 10 ? 0 : resto;
        }
        return digitos;
    }

    private static String mascarar(String d) {
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CatalogoDeEtapas catalogo;

    @Autowired
    AtualizarCaixa atualizarCaixa;

    @MockitoBean
    PosseDoAnalistaEmLote posse;

    @MockitoBean
    RegistrarEvento registrar;

    /** Os requerimentos que a leitura devolve por CPF (dígitos): o dublê da posse. */
    private final AtomicReference<Map<String, List<Long>>> idsPorCpf = new AtomicReference<>(Map.of());

    private String atividadeDeAnalise;
    private String atividadeDeRevisao;
    private String atividadeDeCorrecao;
    private String modelo;

    @BeforeEach
    void preparar() {
        reset(posse, registrar);
        ModeloDeFluxo m = catalogo.modelos().stream()
                .filter(x -> x.etapa(DefinicaoDaPosse.ETAPAS_DE_POSSE.get(0)).isPresent()).findFirst().orElseThrow();
        modelo = m.chave();
        atividadeDeAnalise = m.etapa(DefinicaoDaPosse.ETAPAS_DE_POSSE.get(0)).map(Etapa::idsTecnicos).orElseThrow().get(0);
        atividadeDeRevisao = m.etapa(DefinicaoDaPosse.ETAPAS_DE_POSSE.get(1)).map(Etapa::idsTecnicos).orElseThrow().get(0);
        atividadeDeCorrecao = m.etapa(DefinicaoDaPosse.ETAPA_CORRECAO).map(Etapa::idsTecnicos).orElseThrow().get(0);

        idsPorCpf.set(Map.of(CPF_A_DIGITOS, List.of(18001L, 18002L, 18003L), CPF_B_DIGITOS, List.of(18010L)));
        when(posse.candidatos(anyList(), any(), anyInt(), any())).thenAnswer(c -> {
            List<AnalistaParaLeitura> analistas = c.getArgument(0);
            List<Long> ids = new ArrayList<>();
            analistas.forEach(a -> ids.addAll(idsPorCpf.get().getOrDefault(a.identificador().valorProtegido(), List.of())));
            return ids.stream().sorted().toList();
        });
        when(posse.ler(anyList(), anyList(), any(), any())).thenAnswer(c -> {
            List<AnalistaParaLeitura> analistas = c.getArgument(1);
            List<PosseLida> lidas = new ArrayList<>();
            for (AnalistaParaLeitura a : analistas) {
                List<Long> ids = idsPorCpf.get().getOrDefault(a.identificador().valorProtegido(), List.of());
                for (Long idDoItem : ids) {
                    lidas.add(itemDe(idDoItem, a));
                }
            }
            return new LeituraDaPosse(lidas.stream().sorted((x, y) -> Long.compare(x.idRequerimento(), y.idRequerimento()))
                    .toList(), false);
        });
    }

    /** O i-ésimo requerimento do analista: análise, revisão e (o terceiro) em correção com o interessado. */
    private PosseLida itemDe(long id, AnalistaParaLeitura dono) {
        int i = id == 18001 ? 0 : id == 18002 ? 1 : id == 18003 ? 2 : 0;
        LocalDateTime inicio = LocalDateTime.now().minusDays(10L - i * 3L);
        String interessado = i == 0 ? "Interessado Exemplo 01" : i == 1 ? "Cooperativa Exemplo 04" : "Interessado Exemplo LTDA";
        if (i < 2) {
            String atividade = i == 0 ? atividadeDeAnalise : atividadeDeRevisao;
            return new PosseLida(id, Optional.of(id + "/2028"), modelo,
                    List.of(new PosseLida.TarefaAtivaDaPosse(atividade, inicio, Optional.of(dono.chave()))),
                    Optional.empty(), Optional.of(interessado), Optional.of("Outorga Exemplo"));
        }
        return new PosseLida(id, Optional.of(id + "/2028"), modelo,
                List.of(new PosseLida.TarefaAtivaDaPosse(atividadeDeCorrecao, inicio, Optional.empty())),
                Optional.of(new PosseLida.UltimaTarefaDeAnalise(atividadeDeAnalise, Optional.of(dono.chave()), inicio.minusDays(2))),
                Optional.of(interessado), Optional.of("Outorga Exemplo"));
    }

    // --- apoio

    private String entrar(String login) throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", login, "senha", "senha-de-teste"));
        String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(resposta).get("token").asText();
    }

    private String analista(String login, String cpf) throws Exception {
        Map<String, Object> corpo = new java.util.HashMap<>(Map.of("nome", "Analista Exemplo " + login, "login", login,
                "perfil", "ANALISTA"));
        if (cpf != null) {
            corpo.put("cpfCnpj", cpf);
        }
        MockHttpServletResponse r = mvc.perform(post(USUARIOS).header(HttpHeaders.AUTHORIZATION, entrar("admin.demo"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo))).andReturn().getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return entrar(login);
    }

    private MockHttpServletResponse chamar(String sessao, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, sessao)).andReturn().getResponse();
    }

    private JsonNode corpo(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode caixaPronta(String sessao) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JsonNode atual;
        do {
            atual = corpo(chamar(sessao, get("/api/caixa")));
            if ("PRONTO".equals(atual.get("estado").asText())) {
                return atual;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < limite);
        throw new AssertionError("a caixa não ficou PRONTA: " + atual.get("estado"));
    }

    private void aguardarFimDaAtualizacao() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (atualizarCaixa.emAndamento() && System.nanoTime() < limite) {
            Thread.sleep(20);
        }
    }

    private List<NovoEvento> eventosDaCaixa() {
        ArgumentCaptor<NovoEvento> evento = ArgumentCaptor.forClass(NovoEvento.class);
        verify(registrar, atLeast(0)).registrar(evento.capture());
        return evento.getAllValues().stream().filter(e -> e.tipo() == TipoDeEvento.CAIXA).toList();
    }

    private static void semDadoPessoal(String texto) {
        assertThat(texto).doesNotContain(CPF_A, CPF_A_DIGITOS, CPF_B, CPF_B_DIGITOS)
                .doesNotContainPattern("(?<![0-9])\\d{11}(?![0-9])")
                .doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}").doesNotContainIgnoringCase("chave")
                .doesNotContain("taskAnalisar", "idDaTarefa");
    }

    // --- testes

    @Test
    void operadorGestorEAdministradorLevam403AntesDeQualquerLeituraENadaEhRegistrado() throws Exception {
        String corpoDoVisto = json.writeValueAsString(Map.of("ids", List.of(1)));
        for (String usuario : List.of("operador.demo", "gestor.demo", "admin.demo")) {
            String sessao = entrar(usuario);
            for (var req : List.of(get("/api/caixa"), get("/api/caixa?ordem=INVALIDA"), get("/api/caixa/avisos"),
                    post("/api/caixa/atualizar"),
                    post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON).content(corpoDoVisto))) {
                MockHttpServletResponse r = chamar(sessao, req);
                assertThat(r.getStatus()).as(usuario + " " + req).isEqualTo(403);
                assertThat(r.getContentAsString()).contains("PERFIL_SEM_PERMISSAO");
            }
        }
        for (var req : List.of(get("/api/caixa"), get("/api/caixa/avisos"), post("/api/caixa/atualizar"),
                post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON).content(corpoDoVisto))) {
            assertThat(mvc.perform(req).andReturn().getResponse().getStatus()).isEqualTo(401);
        }
        verify(posse, never()).candidatos(any(), any(), anyInt(), any());
        verify(posse, never()).ler(any(), any(), any(), any());
        assertThat(eventosDaCaixa()).isEmpty();
    }

    @Test
    void analistaVeAPropriaCaixaComResumoFiltrosOrdemEBuscaESoIssoSemCpfNemChave() throws Exception {
        String a = analista("analista.a1", CPF_A);
        String b = analista("analista.b1", CPF_B);

        JsonNode caixa = caixaPronta(a);

        assertThat(caixa.get("semVinculo").asBoolean()).isFalse();
        assertThat(caixa.get("mensagem").isNull()).isTrue();
        assertThat(caixa.get("geradoEm").asText()).endsWith("-04:00");
        assertThat(caixa.get("resumo").get("total").asInt()).isEqualTo(3);
        assertThat(caixa.get("resumo").get("comInteressado").asInt()).isEqualTo(1);
        assertThat(caixa.get("resumo").get("porEtapa")).hasSize(2);
        assertThat(caixa.get("resumo").get("porEtapa").findValuesAsText("rotulo")).doesNotContainNull().allMatch(s -> !s.isBlank());
        assertThat(caixa.get("linhas")).hasSize(3);
        assertThat(caixa.get("linhas").findValuesAsText("interessado"))
                .containsExactlyInAnyOrder("Interessado Exemplo 01", "Cooperativa Exemplo 04", "Interessado Exemplo LTDA");
        assertThat(caixa.get("linhas").findValuesAsText("posse")).containsExactlyInAnyOrder("COM_ANALISTA", "COM_ANALISTA",
                "COM_INTERESSADO");
        // Mais parado primeiro (o padrão): o de 10 dias; mais recente: o de 4.
        assertThat(caixa.get("linhas").get(0).get("id").asLong()).isEqualTo(18001);
        JsonNode recente = corpo(chamar(a, get("/api/caixa?ordem=MAIS_RECENTE")));
        assertThat(recente.get("linhas").get(0).get("id").asLong()).isEqualTo(18003);
        // Filtros: por etapa (a correção), por busca (sem acento e sem caixa) e a visão do resumo não muda.
        JsonNode correcao = corpo(chamar(a, get("/api/caixa?etapa=" + DefinicaoDaPosse.ETAPA_CORRECAO)));
        assertThat(correcao.get("linhas")).hasSize(1);
        assertThat(correcao.get("resumo").get("total").asInt()).isEqualTo(3);
        JsonNode busca = corpo(chamar(a, get("/api/caixa?busca=COOPERATIVA")));
        assertThat(busca.get("linhas")).hasSize(1);
        assertThat(busca.get("linhas").get(0).get("interessado").asText()).isEqualTo("Cooperativa Exemplo 04");

        // A caixa é a da sessão: o outro analista vê só o dele (nenhum parâmetro muda isso).
        JsonNode deB = caixaPronta(b);
        assertThat(deB.get("linhas")).hasSize(1);
        assertThat(deB.get("linhas").get(0).get("id").asLong()).isEqualTo(18010);
        JsonNode tentativa = corpo(chamar(b, get("/api/caixa?analista=analista.a1&login=analista.a1&usuario=analista.a1")));
        assertThat(tentativa.get("linhas")).hasSize(1);
        assertThat(tentativa.get("linhas").get(0).get("id").asLong()).isEqualTo(18010);

        semDadoPessoal(caixa.toString() + recente + correcao + busca + deB + tentativa);
    }

    @Test
    void filtroInvalidoDa400SemDetalheInterno() throws Exception {
        String a = analista("analista.a2", CPF_A);
        for (String caminho : List.of("/api/caixa?ordem=ALEATORIA", "/api/caixa?etapa=taskQualquer",
                "/api/caixa?busca=" + "x".repeat(101))) {
            MockHttpServletResponse r = chamar(a, get(caminho));
            assertThat(r.getStatus()).as(caminho).isEqualTo(400);
            assertThat(r.getContentAsString()).contains("PARAMETRO_INVALIDO").doesNotContain("Exception")
                    .doesNotContain("br.gov.mt");
        }
        MockHttpServletResponse semCorpo = chamar(a, post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
        assertThat(semCorpo.getStatus()).isEqualTo(400);
        assertThat(semCorpo.getContentAsString()).contains("PARAMETRO_INVALIDO");
    }

    @Test
    void analistaSemCpfVeACaixaVaziaComAMensagemDeVinculoENadaEhLido() throws Exception {
        String sem = analista("analista.sem.cpf", null);

        MockHttpServletResponse r = chamar(sem, get("/api/caixa"));

        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode caixa = corpo(r);
        assertThat(caixa.get("semVinculo").asBoolean()).isTrue();
        assertThat(caixa.get("mensagem").asText()).isEqualTo("Seu usuário não está vinculado a um servidor. Peça ao Administrador.");
        assertThat(caixa.get("linhas")).isEmpty();
        assertThat(caixa.get("resumo").get("total").asInt()).isZero();
        JsonNode avisos = corpo(chamar(sem, get("/api/caixa/avisos")));
        assertThat(avisos.get("semVinculo").asBoolean()).isTrue();
        assertThat(avisos.get("avisos")).isEmpty();
        assertThat(chamar(sem, post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[18001]}")).getStatus()).isEqualTo(204);
    }

    @Test
    void primeiroAcessoNaoAvisaEChegadaDepoisAvisaUmaVezEOVistoGravadoFazACaixinhaSumir() throws Exception {
        String a = analista("analista.a3", CPF_A);
        caixaPronta(a);

        // Primeiro acesso: tudo o que já está com o analista entra como visto, sem aviso.
        JsonNode primeira = corpo(chamar(a, get("/api/caixa/avisos")));
        assertThat(primeira.get("quantidade").asInt()).isZero();
        assertThat(primeira.get("avisos")).isEmpty();

        // Chega um requerimento novo e a caixa é atualizada (direto na aplicação: o 429 da rota é provado abaixo).
        idsPorCpf.set(Map.of(CPF_A_DIGITOS, List.of(18001L, 18002L, 18003L, 18004L), CPF_B_DIGITOS, List.of(18010L)));
        aguardarFimDaAtualizacao();
        atualizarCaixa.iniciarEmSegundoPlano();
        aguardarFimDaAtualizacao();

        JsonNode caixa = corpo(chamar(a, get("/api/caixa")));
        assertThat(caixa.get("novos").asInt()).isEqualTo(1);
        assertThat(caixa.get("linhas").findValues("novo").stream().filter(JsonNode::asBoolean)).hasSize(1);
        JsonNode avisos = corpo(chamar(a, get("/api/caixa/avisos")));
        assertThat(avisos.get("quantidade").asInt()).isEqualTo(1);
        JsonNode aviso = avisos.get("avisos").get(0);
        assertThat(aviso.get("id").asLong()).isEqualTo(18004);
        assertThat(aviso.get("interessado").asText()).isNotBlank();
        assertThat(aviso.get("origem").asText()).isNotBlank();
        assertThat(aviso.get("origemCodigo").asText()).isIn("NOVO", "VOLTOU_DA_CORRECAO", "VOLTOU_DA_VALIDACAO",
                "VOLTOU_DA_EMISSAO", "VOLTOU_DA_REVISAO", "MUDOU_DE_ETAPA");
        // Abrir a lista não grava o visto: só o POST grava, e do conjunto devolvido.
        assertThat(corpo(chamar(a, get("/api/caixa/avisos"))).get("quantidade").asInt()).isEqualTo(1);
        assertThat(chamar(a, post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[18004]}")).getStatus()).isEqualTo(204);
        assertThat(corpo(chamar(a, get("/api/caixa/avisos"))).get("quantidade").asInt()).isZero();
        assertThat(corpo(chamar(a, get("/api/caixa"))).get("novos").asInt()).isZero();

        semDadoPessoal(primeira.toString() + caixa + avisos);
    }

    @Test
    void atualizacaoManualAceitaOuRecusaComTentarEmSegundosEOutroAnalistaNaoVeOVistoDoPrimeiro() throws Exception {
        String a = analista("analista.a4", CPF_A);
        caixaPronta(a);
        aguardarFimDaAtualizacao();

        // A primeira leitura acabou de começar (intervalo mínimo de 3600 s no teste): recusada com o tempo que falta.
        MockHttpServletResponse recusada = chamar(a, post("/api/caixa/atualizar"));
        assertThat(recusada.getStatus()).isEqualTo(429);
        JsonNode problema = corpo(recusada);
        assertThat(problema.get("errorCode").asText()).isEqualTo("ATUALIZACAO_RECENTE");
        assertThat(problema.get("tentarEmSegundos").asLong()).isBetween(1L, 3600L);
        assertThat(recusada.getHeader("Retry-After")).isNotBlank();
        assertThat(recusada.getContentAsString()).doesNotContain("Exception").doesNotContain("br.gov.mt");
    }

    @Test
    void auditoriaRegistraSoAQuantidadeEnuncaCpfChaveOuProcessos() throws Exception {
        String a = analista("analista.a5", CPF_A);
        caixaPronta(a);
        chamar(a, get("/api/caixa/avisos"));
        chamar(a, get("/api/caixa?ordem=ALEATORIA"));
        chamar(a, post("/api/caixa/atualizar"));
        chamar(a, post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1]}"));
        String operador = entrar("operador.demo");
        chamar(operador, get("/api/caixa"));

        List<NovoEvento> eventos = eventosDaCaixa();

        assertThat(eventos).isNotEmpty();
        assertThat(eventos).allSatisfy(e -> {
            assertThat(e.detalhe()).startsWith("caixa ").matches("caixa (consulta total \\d+ [A-Z]+|avisos \\d+)");
            assertThat(e.detalhe()).doesNotContain(CPF_A_DIGITOS).doesNotContain("Interessado").doesNotContain("Analista");
            assertThat(e.idRequerimento()).isNull();
        });
        assertThat(eventos.stream().filter(e -> e.detalhe().startsWith("caixa avisos"))).hasSize(1);
        assertThat(eventos.stream().filter(e -> e.detalhe().startsWith("caixa consulta"))).hasSizeGreaterThanOrEqualTo(1);
        // Negado (operador), filtro recusado, visto e atualização manual não registram: só consulta e avisos.
        assertThat(eventos.stream().filter(e -> e.detalhe().contains("ALEATORIA"))).isEmpty();
    }
}
