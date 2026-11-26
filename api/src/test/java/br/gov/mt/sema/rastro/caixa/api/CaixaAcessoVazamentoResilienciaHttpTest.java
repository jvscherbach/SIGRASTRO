package br.gov.mt.sema.rastro.caixa.api;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * S11, bloco 10: acesso, vazamento e resiliência da caixa do analista, pelo contexto completo e a leitura da posse por
 * dublê. Cobre o que o {@code CaixaHttpTest} (contrato) e o {@code MatrizDeAcessoTest} (403 e 401 das rotas novas) não
 * cobrem: o Analista nas rotas dos outros perfis, a varredura de CPF e de chave em respostas, erros, log e auditoria, o
 * Camunda e o SIGA fora do ar, a resposta lenta, o lote parcial, as duas abas e o isolamento da caixa. O "reinício zera o
 * visto" é provado sem Spring em {@code CaixaDoAnalistaTest} (primeiro acesso e reinício). Dados fictícios; os CPFs são
 * calculados na hora (nenhum literal no repositório).
 */
@SpringBootTest(properties = {"rastro.datasource.siga.url=",
        "rastro.caixa.intervalo-minimo-manual-segundos=3600"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ExtendWith(OutputCaptureExtension.class)
class CaixaAcessoVazamentoResilienciaHttpTest {

    private static final String CPF_A_DIGITOS = cpfDeExemplo("123123123");
    private static final String CPF_A = mascarar(CPF_A_DIGITOS);
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

    private final AtomicReference<List<Long>> idsDoAnalista = new AtomicReference<>(List.of(18001L, 18002L));
    private final AtomicReference<Boolean> parcial = new AtomicReference<>(false);
    private String atividadeDeAnalise;
    private String modelo;

    @BeforeEach
    void preparar() {
        reset(posse, registrar);
        ModeloDeFluxo m = catalogo.modelos().stream()
                .filter(x -> x.etapa(DefinicaoDaPosse.ETAPAS_DE_POSSE.get(0)).isPresent()).findFirst().orElseThrow();
        modelo = m.chave();
        atividadeDeAnalise = m.etapa(DefinicaoDaPosse.ETAPAS_DE_POSSE.get(0)).map(Etapa::idsTecnicos).orElseThrow().get(0);
        when(posse.candidatos(anyList(), any(), anyInt(), any())).thenAnswer(c -> idsDoAnalista.get());
        when(posse.ler(anyList(), anyList(), any(), any())).thenAnswer(c -> {
            List<AnalistaParaLeitura> analistas = c.getArgument(1);
            List<PosseLida> lidas = new ArrayList<>();
            for (Long id : idsDoAnalista.get()) {
                LocalDateTime inicio = LocalDateTime.now().minusDays(5);
                lidas.add(new PosseLida(id, Optional.of(id + "/2028"), modelo,
                        List.of(new PosseLida.TarefaAtivaDaPosse(atividadeDeAnalise, inicio,
                                Optional.of(analistas.get(0).chave()))),
                        Optional.empty(), Optional.of("Interessado Exemplo " + id), Optional.of("Outorga Exemplo")));
            }
            return new LeituraDaPosse(lidas, parcial.get());
        });
    }

    // --- apoio

    private String entrar(String login) throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", login, "senha", UsuariosDeTeste.senhaDe(login)));
        String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(resposta).get("token").asText();
    }

    private void cadastrarAnalista(String login, String cpf) throws Exception {
        Map<String, Object> corpo = new HashMap<>(Map.of("nome", "Analista Exemplo " + login, "login", login,
                "perfil", "ANALISTA", "senha", UsuariosDeTeste.senhaDe(login)));
        if (cpf != null) {
            corpo.put("cpfCnpj", cpf);
        }
        MockHttpServletResponse r = mvc.perform(post(USUARIOS).header(HttpHeaders.AUTHORIZATION, entrar("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo))).andReturn().getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
    }

    private String analista(String login, String cpf) throws Exception {
        cadastrarAnalista(login, cpf);
        return entrar(login);
    }

    private MockHttpServletResponse chamar(String sessao, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, sessao)).andReturn().getResponse();
    }

    private JsonNode corpo(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode caixaNoEstado(String sessao, String estado) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JsonNode atual;
        do {
            MockHttpServletResponse r = chamar(sessao, get("/api/caixa"));
            assertThat(r.getStatus()).as("a caixa nunca responde 5xx").isEqualTo(200);
            atual = corpo(r);
            if (estado.equals(atual.get("estado").asText())) {
                return atual;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < limite);
        throw new AssertionError("a caixa não chegou a " + estado + ": " + atual.get("estado"));
    }

    private void aguardarFimDaAtualizacao() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (atualizarCaixa.emAndamento() && System.nanoTime() < limite) {
            Thread.sleep(20);
        }
    }

    static final String UUID_PADRAO = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /** O X-Correlation-Id (UUID aleatório) pode ter 11 dígitos no último grupo: UUIDs saem antes dos padrões. */
    static void semDadoPessoalNemChave(String original) {
        assertThat(original).doesNotContain(CPF_A, CPF_A_DIGITOS);
        String texto = original.replaceAll(UUID_PADRAO, "<uuid>");
        assertThat(texto).doesNotContain(CPF_A, CPF_A_DIGITOS).doesNotContainPattern("(?<![0-9])\\d{11}(?![0-9])")
                .doesNotContainPattern("(?<![0-9])\\d{14}(?![0-9])")
                .doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}")
                .doesNotContainPattern("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}")
                .doesNotContainPattern("(?i)[0-9a-f]{32,}").doesNotContainIgnoringCase("chave")
                .doesNotContain("taskAnalisar", "idDaTarefa");
    }

    static String cpfSinteticoDigitos() {
        return CPF_A_DIGITOS;
    }

    // --- acesso

    @Test
    void oAnalistaSoTemACaixaENegadoNasRotasDeGestorEAdministradorMasUsaAsDaPropriaSessao() throws Exception {
        String a = analista("analista.acesso", CPF_A);
        List<MockHttpServletRequestBuilder> negadas = List.of(
                get("/api/painel"), post("/api/painel/atualizacao"), get("/api/painel/analistas"),
                get("/api/painel/analistas/0000000000000000/requerimentos"),
                get("/api/auditoria/acessos"), get("/api/auditoria/documentos"),
                get(USUARIOS), post(USUARIOS).contentType(MediaType.APPLICATION_JSON).content("{}"),
                patch(USUARIOS + "/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/configuracoes/parametros"),
                put("/api/configuracoes/parametros").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/configuracoes/parametros/rastro.caixaAtualizacaoMinutos"),
                get("/api/configuracoes/conexoes"), post("/api/configuracoes/conexoes/siga/teste"));

        for (MockHttpServletRequestBuilder req : negadas) {
            MockHttpServletResponse r = chamar(a, req);
            assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(403);
            assertThat(r.getContentAsString()).contains("PERFIL_SEM_PERMISSAO");
            semDadoPessoalNemChave(r.getContentAsString());
        }
        // A sessão do Analista vale nas rotas comuns e na própria caixa.
        assertThat(chamar(a, get("/api/auth/me")).getStatus()).isEqualTo(200);
        assertThat(chamar(a, get("/api/estado")).getStatus()).isEqualTo(200);
        assertThat(chamar(a, get("/api/caixa")).getStatus()).isEqualTo(200);
        // Token inválido e sessão encerrada: 401, nunca a caixa de alguém.
        assertThat(chamar("Bearer token-inventado", get("/api/caixa")).getStatus()).isEqualTo(401);
        assertThat(chamar(a, post("/api/auth/logout")).getStatus()).isEqualTo(204);
        assertThat(chamar(a, get("/api/caixa")).getStatus()).isEqualTo(401);
    }

    // --- vazamento

    @Test
    void nenhumCpfNemChaveEmRespostaErroLogOuAuditoriaDeTodoOFluxoDaCaixa(CapturedOutput saida) throws Exception {
        String a = analista("analista.vaza", CPF_A);
        List<String> respostas = new ArrayList<>();
        respostas.add(caixaNoEstado(a, "PRONTO").toString());
        for (MockHttpServletRequestBuilder req : List.of(
                get("/api/caixa?etapa=" + DefinicaoDaPosse.ETAPA_CORRECAO), get("/api/caixa?busca=exemplo"),
                get("/api/caixa?ordem=MAIS_RECENTE"), get("/api/caixa/avisos"),
                get("/api/caixa?ordem=INVALIDA"), get("/api/caixa?etapa=taskQualquer"),
                get("/api/caixa?busca=" + CPF_A_DIGITOS), get("/api/caixa?busca=" + CPF_A),
                post("/api/caixa/atualizar"),
                post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[18001]}"))) {
            MockHttpServletResponse r = chamar(a, req);
            respostas.add(r.getContentAsString(StandardCharsets.UTF_8));
            respostas.add(r.getHeaderNames().stream().map(h -> h + "=" + r.getHeaders(h)).toList().toString());
        }
        // Erros dos outros perfis e de quem tenta ler a caixa pelos parâmetros.
        for (String usuario : List.of("operador", "gestor", "admin")) {
            respostas.add(chamar(entrar(usuario), get("/api/caixa?analista=" + CPF_A_DIGITOS)).getContentAsString());
        }
        respostas.add(chamar(a, get("/api/caixa?analista=" + CPF_A_DIGITOS + "&cpf=" + CPF_A)).getContentAsString());
        // Lista de usuários (o CPF do Analista nunca sai, nem mascarado) e perfil.
        respostas.add(chamar(entrar("admin"), get(USUARIOS)).getContentAsString());
        respostas.add(chamar(a, get("/api/auth/me")).getContentAsString());

        respostas.forEach(CaixaAcessoVazamentoResilienciaHttpTest::semDadoPessoalNemChave);

        // Log: nem o CPF (puro ou mascarado) nem a chave opaca do analista aparecem na saída do processo.
        assertThat(saida.getAll()).doesNotContain(CPF_A, CPF_A_DIGITOS);

        // Auditoria: nenhum evento, de nenhum tipo, carrega o CPF ou lista de processos.
        ArgumentCaptor<NovoEvento> eventos = ArgumentCaptor.forClass(NovoEvento.class);
        verify(registrar, atLeast(1)).registrar(eventos.capture());
        // O correlationId é um UUID aleatório (técnico, não é dado pessoal): sem ele, um trecho de 11 dígitos do UUID
        // não vira falso positivo de CPF.
        assertThat(eventos.getAllValues()).allSatisfy(e -> assertThat(e.toString().replaceAll("correlationId=[^,]*", ""))
                .doesNotContain(CPF_A, CPF_A_DIGITOS)
                .doesNotContainPattern("(?<![0-9])\\d{11}(?![0-9])").doesNotContain("18001", "18002"));
    }

    // --- resiliência

    @Test
    void camundaOuSigaForaDoArDaCaixaIndisponivelSemAfetarOsOutrosModulosESemDetalheInterno() throws Exception {
        when(posse.candidatos(anyList(), any(), anyInt(), any())).thenThrow(new IllegalStateException(
                "falha-simulada-do-camunda jdbc:oracle:thin:@servidor-interno"));
        String a = analista("analista.fora", CPF_A);
        String gestor = entrar("gestor");

        MockHttpServletResponse primeira = chamar(a, get("/api/caixa"));
        JsonNode indisponivel = caixaNoEstado(a, "INDISPONIVEL");

        assertThat(primeira.getStatus()).isEqualTo(200);
        assertThat(indisponivel.get("linhas")).isEmpty();
        assertThat(indisponivel.toString()).doesNotContain("falha-simulada").doesNotContain("jdbc").doesNotContain("Exception");
        MockHttpServletResponse avisos = chamar(a, get("/api/caixa/avisos"));
        assertThat(avisos.getStatus()).isEqualTo(200);
        assertThat(avisos.getContentAsString()).doesNotContain("falha-simulada").doesNotContain("jdbc");
        // Isolamento: a falha da caixa não derruba nenhum outro módulo, para o Analista nem para os outros perfis.
        assertThat(chamar(a, get("/api/estado")).getStatus()).isEqualTo(200);
        assertThat(chamar(a, get("/api/auth/me")).getStatus()).isEqualTo(200);
        assertThat(chamar(gestor, get("/api/painel")).getStatus()).isEqualTo(200);
        assertThat(chamar(gestor, get("/api/estado")).getStatus()).isEqualTo(200);
        assertThat(chamar(entrar("operador"), get("/api/estado")).getStatus()).isEqualTo(200);
        assertThat(chamar(gestor, get("/api/caixa")).getStatus()).isEqualTo(403);
    }

    @Test
    void respostaLentaNuncaSeguraARequisicaoECaixaFicaPreparandoAteALeituraAcabar() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        when(posse.candidatos(anyList(), any(), anyInt(), any())).thenAnswer(c -> {
            liberar.await(15, TimeUnit.SECONDS);
            return idsDoAnalista.get();
        });
        String a = analista("analista.lento", CPF_A);

        long inicio = System.nanoTime();
        JsonNode primeira = corpo(chamar(a, get("/api/caixa")));
        JsonNode segunda = corpo(chamar(a, get("/api/caixa")));
        long gastoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

        assertThat(gastoMs).as("as requisições não esperam a leitura lenta").isLessThan(3000);
        assertThat(primeira.get("estado").asText()).isEqualTo("PREPARANDO");
        assertThat(segunda.get("estado").asText()).isEqualTo("PREPARANDO");
        assertThat(segunda.get("linhas")).isEmpty();
        // Várias requisições durante a leitura não disparam outra (single flight).
        verify(posse, org.mockito.Mockito.times(1)).candidatos(anyList(), any(), anyInt(), any());

        liberar.countDown();
        assertThat(caixaNoEstado(a, "PRONTO").get("linhas")).hasSize(2);
    }

    @Test
    void loteParcialApareceComoIncompletoSemPerderOsItensLidos() throws Exception {
        parcial.set(true);
        String a = analista("analista.parcial", CPF_A);

        JsonNode caixa = caixaNoEstado(a, "PRONTO");

        assertThat(caixa.get("incompleta").asBoolean()).isTrue();
        assertThat(caixa.get("linhas")).hasSize(2);
        semDadoPessoalNemChave(caixa.toString());
    }

    @Test
    void analistaSemCpfNaoDisparaNenhumaLeituraDeCamundaNemDeSiga() throws Exception {
        String sem = analista("analista.sem.cpf.10", null);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse r = chamar(sem, get("/api/caixa"));
            assertThat(r.getStatus()).isEqualTo(200);
            assertThat(corpo(r).get("semVinculo").asBoolean()).isTrue();
        }
        assertThat(chamar(sem, get("/api/caixa/avisos")).getStatus()).isEqualTo(200);

        org.mockito.Mockito.verifyNoInteractions(posse);
    }

    @Test
    void duasAbasDoMesmoAnalistaOVistoDeUmaNaoQuebraNemApagaOQueAOutraAindaNaoViu() throws Exception {
        String abaUm = analista("analista.abas", CPF_A);
        String abaDois = entrar("analista.abas");
        caixaNoEstado(abaUm, "PRONTO");
        // Primeiro acesso: nada avisa. Depois chegam dois requerimentos.
        assertThat(corpo(chamar(abaUm, get("/api/caixa/avisos"))).get("quantidade").asInt()).isZero();
        idsDoAnalista.set(List.of(18001L, 18002L, 18003L, 18004L));
        aguardarFimDaAtualizacao();
        atualizarCaixa.iniciarEmSegundoPlano();
        aguardarFimDaAtualizacao();

        assertThat(corpo(chamar(abaUm, get("/api/caixa/avisos"))).get("quantidade").asInt()).isEqualTo(2);
        assertThat(corpo(chamar(abaDois, get("/api/caixa/avisos"))).get("quantidade").asInt()).isEqualTo(2);
        // A aba 1 viu só o 18003: o 18004 continua avisando para a aba 2 e para a própria aba 1.
        assertThat(chamar(abaUm, post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[18003]}")).getStatus()).isEqualTo(204);
        JsonNode daAbaDois = corpo(chamar(abaDois, get("/api/caixa/avisos")));
        assertThat(daAbaDois.get("quantidade").asInt()).isEqualTo(1);
        assertThat(daAbaDois.get("avisos").get(0).get("id").asLong()).isEqualTo(18004);
        // A aba 2 grava o conjunto que ela mesma recebeu (repetir o visto é inofensivo) e nada mais avisa.
        assertThat(chamar(abaDois, post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[18003,18004]}")).getStatus()).isEqualTo(204);
        assertThat(corpo(chamar(abaUm, get("/api/caixa/avisos"))).get("quantidade").asInt()).isZero();
        assertThat(corpo(chamar(abaDois, get("/api/caixa"))).get("novos").asInt()).isZero();
    }

    @Test
    void oVistoNaoSobreviveAoReinicioPrimeiroAcessoDoNovoContextoNaoTemAvisoRetroativo() throws Exception {
        // O reinício é a recriação do contexto (o visto e a chave do analista vivem só em memória): o contexto novo parte
        // sem visto, então tudo o que já está com o analista entra como visto e nada avisa. Aqui, o contexto é o do teste.
        String a = analista("analista.reinicio", CPF_A);
        caixaNoEstado(a, "PRONTO");

        JsonNode avisos = corpo(chamar(a, get("/api/caixa/avisos")));

        assertThat(avisos.get("quantidade").asInt()).isZero();
        assertThat(avisos.get("avisos")).isEmpty();
        assertThat(corpo(chamar(a, get("/api/caixa"))).get("novos").asInt()).isZero();
    }
}
