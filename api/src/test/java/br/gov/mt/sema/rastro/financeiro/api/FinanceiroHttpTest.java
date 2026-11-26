package br.gov.mt.sema.rastro.financeiro.api;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.consulta.application.ObjetoEPrioridadeDoRequerimento;
import br.gov.mt.sema.rastro.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.rastro.consulta.application.TarefaAtualDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.CriterioBusca;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.Pagina;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.ResumoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.consulta.domain.TipoBusca;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.domain.Lancamento;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import br.gov.mt.sema.rastro.tramitacao.application.MontarTramitacao;
import br.gov.mt.sema.rastro.tramitacao.application.TramitacoesDeTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Contrato HTTP do histórico financeiro (S9, bloco 6), por perfil e cenário, com o financeiro de verdade e os
 * lançamentos fictícios por dublê da porta (o SQL é provado em {@code LancamentosDoSigaJdbcTest}). Cenários no espírito
 * do FIN01, FIN03, FIN05, FIN07, FIN10 e FIN13; vencimentos longe de hoje (o relógio é o do projeto).
 */
@SpringBootTest(properties = {"rastro.datasource.siga.url=",
        "rastro.tramitacao.tramitacao-cache-segundos=0"})
@AutoConfigureMockMvc
class FinanceiroHttpTest {

    private static final long PAGA = 9001;
    private static final long VENCIDA_POR_DATA = 9003;
    private static final long ISENTA = 9005;
    private static final long FALSO_E_VALIDO = 9007;
    private static final long SEM_TAXA = 9013;
    private static final long BANCO_FORA = 9020;
    private static final long DE_OUTRO = 9021;
    private static final String PROCESSO = "9900/2026";
    private static final String CNPJ_DO_OPERADOR = "11.222.333/0001-81";
    private static final String CPF_DE_OUTRO = "000.000.000-91";
    private static final LocalDate VENCEU = LocalDate.of(2020, 1, 10);
    private static final LocalDate VENCE = LocalDate.of(2099, 12, 31);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 9, 0);
    private static final String DAR = "FICT-0001";

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
    MontarTramitacao montar;

    @MockitoBean
    RegistrarEvento registrar;

    @MockitoBean
    LancamentosDoRequerimento lancamentos;

    private final Map<Long, List<Lancamento>> porRequerimento = new HashMap<>();
    private final List<Requerimento> requerimentos = new ArrayList<>();

    @BeforeEach
    void preparar() {
        reset(siga, montar, registrar, lancamentos);
        porRequerimento.clear();
        requerimentos.clear();
        for (long id : List.of(PAGA, VENCIDA_POR_DATA, ISENTA, FALSO_E_VALIDO, SEM_TAXA, BANCO_FORA)) {
            requerimentos.add(novo(id, CNPJ_DO_OPERADOR));
        }
        requerimentos.add(novo(DE_OUTRO, CPF_DE_OUTRO));
        for (Requerimento r : requerimentos) {
            when(siga.buscarPorId(r.id())).thenReturn(Optional.of(r));
        }
        when(siga.buscarPorId(404L)).thenReturn(Optional.empty());
        when(siga.buscarPorCriterios(any())).thenAnswer(c -> processo(c.getArgument(0)));
        // O Camunda fora do ar: o financeiro perde só o rótulo "da correção" e a data de apoio da isenção.
        when(montar.montar(anyLong())).thenReturn(TramitacoesDeTeste.indisponivel());

        porRequerimento.put(PAGA, List.of(guia(1, PAGA, "PAGA", DAR, T0.plusDays(2), VENCE, T0.plusDays(5))));
        porRequerimento.put(VENCIDA_POR_DATA, List.of(
                guia(2, VENCIDA_POR_DATA, "AGUARDANDO_PAGAMENTO", "FICT-0003", T0, VENCEU, null)));
        porRequerimento.put(ISENTA, List.of(new Lancamento(3, ISENTA, "ISENTA", true, "N", null, T0.plusDays(2), null, null,
                null, null, null, null, null)));
        porRequerimento.put(FALSO_E_VALIDO, List.of(
                new Lancamento(4, FALSO_E_VALIDO, "CANCELADA", false, "P", null, T0, null, null, T0.plusHours(1), null,
                        null, null, null),
                guia(5, FALSO_E_VALIDO, "AGUARDANDO_PAGAMENTO", "FICT-0007", T0.plusHours(2), VENCE, null),
                new Lancamento(6, FALSO_E_VALIDO, "AGUARDANDO_EMISSAO", true, "P", null, null, null, null, null, null,
                        null, null, null)));
        when(lancamentos.lerDoRequerimento(anyLong())).thenAnswer(c -> leitura(c.getArgument(0)));
        when(lancamentos.lerDoRequerimento(BANCO_FORA)).thenThrow(new DataAccessResourceFailureException("fora"));
        when(lancamentos.lerEmLote(anyCollection())).thenThrow(new IllegalStateException("não deve ler em lote"));
    }

    private static Requerimento novo(long id, String cpfCnpj) {
        return new Requerimento(id, id + "/2026", PROCESSO, "Requerimento de Outorga", Situacao.de("EM_ANALISE"), T0, null,
                new Interessado("Interessado Fictício S9", cpfCnpj), "Outorga (FICTÍCIO)");
    }

    private LeituraDosLancamentos leitura(long id) {
        return new LeituraDosLancamentos(porRequerimento.getOrDefault(id, List.of()), false);
    }

    private static Lancamento guia(long id, long requerimento, String status, String dar, LocalDateTime emissao,
            LocalDate vencimento, LocalDateTime pagamento) {
        return new Lancamento(id, requerimento, status, false, "P", dar, emissao, vencimento.atStartOfDay(), pagamento,
                null, new BigDecimal("980.00"), BigDecimal.ZERO, new BigDecimal("28.0000"), null);
    }

    private Pagina<ResumoRequerimento> processo(CriterioBusca criterio) {
        List<ResumoRequerimento> itens = new ArrayList<>();
        if (criterio.tipo() == TipoBusca.PROCESSO) {
            for (Requerimento r : requerimentos) {
                boolean permitido = criterio.restricaoDigitos() == null
                        || r.cpfCnpjDoInteressado().replaceAll("\\D", "").equals(criterio.restricaoDigitos());
                if (permitido) {
                    itens.add(r.resumo());
                }
            }
        }
        return new Pagina<>(itens, itens.size(), criterio.pagina(), criterio.tamanho());
    }

    private ResultActions obter(String usuario, String caminho) throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", usuario, "senha", UsuariosDeTeste.senhaDe(usuario)));
        String token = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo)).andReturn().getResponse().getContentAsString()).get("token").asText();
        return mvc.perform(get(caminho).header("Authorization", "Bearer " + token));
    }

    private JsonNode ok(String usuario, String caminho) throws Exception {
        return json.readTree(texto(obter(usuario, caminho).andExpect(status().isOk())));
    }

    private static String texto(ResultActions resposta) throws Exception {
        return resposta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<NovoEvento> eventosFinanceiros() {
        ArgumentCaptor<NovoEvento> evento = ArgumentCaptor.forClass(NovoEvento.class);
        verify(registrar, org.mockito.Mockito.atLeast(0)).registrar(evento.capture());
        return evento.getAllValues().stream().filter(x -> x.tipo() == TipoDeEvento.FINANCEIRO).toList();
    }

    private static List<String> codigos(JsonNode avisos) {
        List<String> c = new ArrayList<>();
        avisos.forEach(a -> c.add(a.get("codigo").asText()));
        return c;
    }

    // --- o histórico

    @Test
    void gestorVeATaxaPagaComDarDatasEValoresMasNenhumIdNemDiagnostico() throws Exception {
        String corpo = texto(obter("gestor", "/api/requerimentos/" + PAGA + "/financeiro")
                .andExpect(status().isOk()));
        JsonNode r = json.readTree(corpo);
        assertThat(r.get("estado").asText()).isEqualTo("ATIVO");
        assertThat(r.get("resumo").asText()).isEqualTo("QUITADO");
        assertThat(r.get("mostraValores").asBoolean()).isTrue();
        assertThat(r.get("lancamentos")).hasSize(1);
        JsonNode l = r.get("lancamentos").get(0);
        assertThat(l.get("ordem").asInt()).isEqualTo(1);
        assertThat(l.get("rotulo").asText()).isEqualTo("1ª taxa");
        assertThat(l.get("situacao").asText()).isEqualTo("PAGA");
        assertThat(l.get("numeroDar").asText()).isEqualTo(DAR);
        assertThat(l.get("emitidaEm").asText()).isEqualTo("2026-03-04");
        assertThat(l.get("venceEm").asText()).isEqualTo("2099-12-31");
        assertThat(l.get("pagaEm").asText()).isEqualTo("2026-03-07");
        assertThat(l.get("valor").decimalValue()).isEqualByComparingTo("980.00");
        assertThat(l.get("upf").decimalValue()).isEqualByComparingTo("28.0000");
        assertThat(r.get("isencoes")).isEmpty();
        assertThat(r.get("avisos")).isEmpty();
        // Nada de Administrador, de chave de arquivo, de mensagem nem de titulação.
        assertThat(corpo).doesNotContain("idTecnico").doesNotContain("ignoradosPorMotivo").doesNotContain("statusProcessado")
                .doesNotContain("passouPelaReaprovacao").doesNotContain("inconsistencias").doesNotContain("origemDaData")
                .doesNotContain("naoProcessado").doesNotContain("chave").doesNotContain("mensagem")
                .doesNotContain("titulacao").doesNotContain("Ged")
                .doesNotContainPattern("(?<![0-9])\\d{11}(?![0-9])").doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    }

    @Test
    void taxaVencidaPorDataMantemASituacaoDoSigaEAcrescentaOAviso() throws Exception {
        JsonNode r = ok("gestor", "/api/requerimentos/" + VENCIDA_POR_DATA + "/financeiro");
        assertThat(r.get("resumo").asText()).isEqualTo("VENCIDO");
        JsonNode l = r.get("lancamentos").get(0);
        assertThat(l.get("situacao").asText()).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(l.get("vencidaPorData").asBoolean()).isTrue();
        assertThat(l.get("vencida").asBoolean()).isTrue();
        assertThat(codigos(r.get("avisos"))).containsExactly("TAXA_VENCIDA");
        assertThat(r.get("avisos").get(0).get("texto").asText()).contains("10/01/2020");
    }

    @Test
    void isentoTemUmaEntradaComDataDoLancamentoESemDarNemRotuloDeTaxa() throws Exception {
        JsonNode r = ok("gestor", "/api/requerimentos/" + ISENTA + "/financeiro");
        assertThat(r.get("resumo").asText()).isEqualTo("ISENTO");
        assertThat(r.get("isencoes")).hasSize(1);
        JsonNode i = r.get("isencoes").get(0);
        assertThat(i.get("texto").asText()).isEqualTo("Isento de Taxa");
        assertThat(i.get("ordem").asInt()).isEqualTo(1);
        assertThat(i.get("data").asText()).isEqualTo("2026-03-04");
        assertThat(i.get("dataAConfirmar").asBoolean()).isFalse();
        JsonNode l = r.get("lancamentos").get(0);
        assertThat(l.get("situacao").asText()).isEqualTo("ISENTA");
        assertThat(l.get("rotulo").isNull()).isTrue();
        assertThat(l.get("numeroDar").isNull()).isTrue();
        assertThat(i.has("origemDaData")).isFalse();
    }

    @Test
    void semLancamentoEhSemTaxa() throws Exception {
        JsonNode r = ok("gestor", "/api/requerimentos/" + SEM_TAXA + "/financeiro");
        assertThat(r.get("estado").asText()).isEqualTo("ATIVO");
        assertThat(r.get("resumo").asText()).isEqualTo("SEM_TAXA");
        assertThat(r.get("lancamentos")).isEmpty();
        assertThat(r.get("isencoes")).isEmpty();
    }

    @Test
    void ignoradosNaoAparecemParaGestorNemOperadorEAdministradorVeOsContadosPorMotivo() throws Exception {
        for (String usuario : List.of("gestor", "operador")) {
            String corpo = texto(obter(usuario, "/api/requerimentos/" + FALSO_E_VALIDO + "/financeiro")
                    .andExpect(status().isOk()));
            JsonNode r = json.readTree(corpo);
            assertThat(r.get("lancamentos")).as(usuario).hasSize(1);
            assertThat(r.get("lancamentos").get(0).get("numeroDar").asText()).isEqualTo("FICT-0007");
            assertThat(corpo).doesNotContain("FALSO_SEM_DAR").doesNotContain("RUIDO_ISENTO_AGUARDANDO")
                    .doesNotContain("ignorados");
        }
        JsonNode admin = ok("admin", "/api/requerimentos/" + FALSO_E_VALIDO + "/financeiro");
        assertThat(admin.get("ignoradosPorMotivo").get("FALSO_SEM_DAR").asInt()).isEqualTo(1);
        assertThat(admin.get("ignoradosPorMotivo").get("RUIDO_ISENTO_AGUARDANDO").asInt()).isEqualTo(1);
    }

    @Test
    void administradorVeIdsTecnicosEOsCamposDeDiagnostico() throws Exception {
        JsonNode r = ok("admin", "/api/requerimentos/" + ISENTA + "/financeiro");
        JsonNode l = r.get("lancamentos").get(0);
        assertThat(l.get("idTecnico").asLong()).isEqualTo(3L);
        assertThat(l.get("statusProcessado").asText()).isEqualTo("N");
        assertThat(l.get("inconsistencias")).isEmpty();
        JsonNode i = r.get("isencoes").get(0);
        assertThat(i.get("origemDaData").asText()).isEqualTo("LANCAMENTO");
        assertThat(i.get("naoProcessado").asBoolean()).isTrue();
    }

    // --- falha e independência

    @Test
    void bancoForaDoArEh200ComIndisponivelERegistraAConsulta() throws Exception {
        String corpo = texto(obter("gestor", "/api/requerimentos/" + BANCO_FORA + "/financeiro")
                .andExpect(status().isOk()));
        JsonNode r = json.readTree(corpo);
        assertThat(r.get("estado").asText()).isEqualTo("INDISPONIVEL");
        assertThat(r.get("resumo").isNull()).isTrue();
        assertThat(r.get("lancamentos")).isEmpty();
        assertThat(corpo).doesNotContain("fora");
        assertThat(eventosFinanceiros()).hasSize(1);
    }

    // --- acesso

    @Test
    void operadorLeSoOQueEDele() throws Exception {
        ok("operador", "/api/requerimentos/" + PAGA + "/financeiro");
        obter("operador", "/api/requerimentos/" + DE_OUTRO + "/financeiro")
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode")
                        .value("NAO_INTERESSADO"));
        verify(lancamentos, never()).lerDoRequerimento(DE_OUTRO);
    }

    @Test
    void requerimentoInexistenteEh404SemLerNemRegistrar() throws Exception {
        for (String usuario : List.of("operador", "gestor", "admin")) {
            obter(usuario, "/api/requerimentos/404/financeiro").andExpect(status().isNotFound());
        }
        verify(lancamentos, never()).lerDoRequerimento(404L);
        assertThat(eventosFinanceiros()).isEmpty();
    }

    @Test
    void semTokenEh401() throws Exception {
        mvc.perform(get("/api/requerimentos/" + PAGA + "/financeiro")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/processos/9900-2026/financeiro")).andExpect(status().isUnauthorized());
        verify(lancamentos, never()).lerDoRequerimento(anyLong());
    }

    // --- processo (1:N)

    @Test
    void processoDevolveUmHistoricoPorRequerimentoVisivelEOperadorSoVeOsSeus() throws Exception {
        JsonNode gestor = ok("gestor", "/api/processos/9900-2026/financeiro");
        assertThat(gestor).hasSize(requerimentos.size());
        JsonNode operador = ok("operador", "/api/processos/9900-2026/financeiro");
        assertThat(operador).hasSize(requerimentos.size() - 1);
        operador.forEach(r -> assertThat(r.get("idRequerimento").asLong()).isNotEqualTo(DE_OUTRO));
        // Um banco fora do ar num requerimento não derruba a lista.
        JsonNode fora = null;
        for (JsonNode r : gestor) {
            if (r.get("idRequerimento").asLong() == BANCO_FORA) {
                fora = r;
            }
        }
        assertThat(fora).isNotNull();
        assertThat(fora.get("estado").asText()).isEqualTo("INDISPONIVEL");
    }

    // --- auditoria

    @Test
    void registraFinanceiroComUsuarioENumerosSemValorNemDarNemCpf() throws Exception {
        ok("gestor", "/api/requerimentos/" + PAGA + "/financeiro");

        List<NovoEvento> eventos = eventosFinanceiros();
        assertThat(eventos).hasSize(1);
        NovoEvento e = eventos.getFirst();
        assertThat(e.tipo().grupo().name()).isEqualTo("DOCUMENTOS");
        assertThat(e.usuario().login()).isEqualTo("gestor");
        assertThat(e.idRequerimento()).isEqualTo(PAGA);
        assertThat(e.numeroRequerimento()).isEqualTo(PAGA + "/2026");
        assertThat(e.numeroProcesso()).isEqualTo(PROCESSO);
        String retrato = e.toString();
        assertThat(retrato).contains("financeiro").doesNotContain(DAR).doesNotContain("980").doesNotContain("28.0")
                .doesNotContain("PAGA");
    }

    @Test
    void acessoNegadoNaoRegistra() throws Exception {
        obter("operador", "/api/requerimentos/" + DE_OUTRO + "/financeiro").andExpect(status().isForbidden());
        assertThat(eventosFinanceiros()).isEmpty();
    }
}
