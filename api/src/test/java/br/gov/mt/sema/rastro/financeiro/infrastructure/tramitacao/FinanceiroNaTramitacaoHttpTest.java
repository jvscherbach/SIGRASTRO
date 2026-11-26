package br.gov.mt.sema.rastro.financeiro.infrastructure.tramitacao;

import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.quandoLer;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
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
import br.gov.mt.sema.rastro.tramitacao.application.ConsultarPosicaoAtual;
import br.gov.mt.sema.rastro.tramitacao.application.MontarTramitacao;
import br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste;
import br.gov.mt.sema.rastro.tramitacao.application.TramitacoesDeTeste;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido;
import br.gov.mt.sema.rastro.tramitacao.domain.DesfechoDoCaminho;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O financeiro na tramitação (S9, bloco 5), pela API real da posição e da tramitação, com o financeiro de verdade e os
 * lançamentos fictícios por dublê da porta ({@code LANCAMENTO_TAXA} é provado em {@code LancamentosDoSigaJdbcTest}).
 * Cenários no espírito do FIN03, FIN11, FIN15 e FIN16; vencimentos longe de hoje (o relógio é o do projeto).
 */
@SpringBootTest(properties = {"rastro.datasource.siga.url=",
        "rastro.tramitacao.tramitacao-cache-segundos=0"})
@AutoConfigureMockMvc
class FinanceiroNaTramitacaoHttpTest {

    private static final long FIN03 = 9003;
    private static final long FIN11 = 9011;
    private static final long FIN15 = 9015;
    private static final long FIN16 = 9016;
    private static final long SEM_TAXA = 9013;
    private static final long CAMUNDA_FORA = 9020;
    private static final String PROCESSO = "9900/2026";
    private static final LocalDate VENCEU = LocalDate.of(2020, 1, 10);
    private static final LocalDate VENCE = LocalDate.of(2099, 12, 31);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 9, 0);
    private static final ModeloDeFluxo HIDRICO = CatalogoDeEtapasDeTeste.padrao().modelo("procOutorgaAguaSupSub")
            .orElseThrow();

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

    private final Map<Long, List<Lancamento>> porRequerimento = new HashMap<>();
    private final List<Requerimento> requerimentos = new ArrayList<>();

    @BeforeEach
    void preparar() {
        reset(siga, consultar, montar, registrar, lancamentos);
        porRequerimento.clear();
        requerimentos.clear();
        for (long id : List.of(FIN03, FIN11, FIN15, FIN16, SEM_TAXA, CAMUNDA_FORA)) {
            Requerimento r = new Requerimento(id, id + "/2026", PROCESSO, "Requerimento de Outorga",
                    Situacao.de("AGUARDANDO_PAGAMENTO_DE_TAXAS"), T0, null,
                    new Interessado("Interessado Fictício S9", "11.222.333/0001-81"), "Outorga (FICTÍCIO)");
            requerimentos.add(r);
            when(siga.buscarPorId(id)).thenReturn(Optional.of(r));
            quandoLer(consultar, id, PosicoesDeTeste.ativaComServidor());
        }
        when(siga.buscarPorCriterios(any())).thenAnswer(c -> processo(c.getArgument(0)));
        quandoLer(consultar, FIN15, PosicoesDeTeste.encerradaPorTaxa());
        quandoLer(consultar, CAMUNDA_FORA, PosicoesDeTeste.indisponivel());

        porRequerimento.put(FIN03, List.of(guia(1, FIN03, "AGUARDANDO_PAGAMENTO", "FICT-0003", T0, VENCEU, null)));
        porRequerimento.put(FIN15, List.of(guia(2, FIN15, "AGUARDANDO_PAGAMENTO", "FICT-0015", T0, VENCEU, null)));
        porRequerimento.put(FIN11, List.of(
                guia(3, FIN11, "PAGA", "FICT-0011", T0.plusDays(2), VENCE, T0.plusDays(5)),
                guia(4, FIN11, "AGUARDANDO_PAGAMENTO", "FICT-0111", T0.plusDays(14), VENCE, null)));
        porRequerimento.put(FIN16, List.of(guia(5, FIN16, "PAGA", "FICT-0016", T0.plusDays(2), VENCE, T0.plusDays(5))));
        porRequerimento.put(CAMUNDA_FORA, List.of(
                guia(6, CAMUNDA_FORA, "AGUARDANDO_PAGAMENTO", "FICT-0020", T0, VENCEU, null)));
        when(lancamentos.lerDoRequerimento(anyLong())).thenAnswer(c -> leitura(c.getArgument(0)));
        when(lancamentos.lerEmLote(anyCollection())).thenAnswer(c -> {
            Map<Long, LeituraDosLancamentos> lote = new HashMap<>();
            for (Object id : (Collection<?>) c.getArgument(0)) {
                lote.put((Long) id, leitura((Long) id));
            }
            return lote;
        });

        when(montar.montar(FIN11)).thenReturn(fin11());
        when(montar.montar(FIN16)).thenReturn(TramitacoesDeTeste.planoB());
        when(montar.montar(CAMUNDA_FORA)).thenReturn(TramitacoesDeTeste.indisponivel());
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
        List<ResumoRequerimento> itens = criterio.tipo() == TipoBusca.PROCESSO
                ? requerimentos.stream().map(Requerimento::resumo).toList() : List.of();
        return new Pagina<>(itens, itens.size(), criterio.pagina(), criterio.tamanho());
    }

    private static PassagemPelaEtapa passagem(String etapa, Ramo ramo, LocalDateTime entrada) {
        return new PassagemPelaEtapa(Optional.of(HIDRICO.etapa(etapa).orElseThrow()), "idFicticio", 1, ramo, entrada,
                Optional.of(entrada.plusHours(1)), List.of(), false, List.of(), Optional.empty(), false, 1, false);
    }

    /** FIN11: taxa inicial paga e a da correção aguardando (correção a partir do dia 12, nova taxa no dia 14). */
    private static TramitacaoDoRequerimento fin11() {
        List<PassagemPelaEtapa> passagens = List.of(
                passagem("cadastro_do_requerimento", Ramo.PRINCIPAL, T0),
                passagem("emissao_da_taxa", Ramo.PRINCIPAL, T0.plusDays(2)),
                passagem("aguardando_pagamento", Ramo.PRINCIPAL, T0.plusDays(2).plusHours(1)),
                passagem("correcao_do_requerimento", Ramo.CORRECAO, T0.plusDays(12)),
                passagem("emissao_da_taxa_correcao", Ramo.CORRECAO, T0.plusDays(14)),
                passagem("aguardando_pagamento_correcao", Ramo.CORRECAO, T0.plusDays(14).plusHours(1)));
        CaminhoReconstruido caminho = new CaminhoReconstruido(passagens, List.of(),
                CaminhoReconstruido.ClassificacaoDaTaxa.TAXAVEL, Optional.of(CaminhoReconstruido.ClassificacaoDaTaxa.TAXAVEL),
                Set.of(), 0, Set.of(), false, 1);
        return new TramitacaoDoRequerimento(TramitacaoDoRequerimento.Estado.ATIVO, Optional.of(HIDRICO.chave()),
                Optional.of("AGUARDANDO_PAGAMENTO_DE_TAXAS"), caminho, Optional.of(new DesfechoDoCaminho(
                        DesfechoDoCaminho.Estado.EM_ANDAMENTO, Optional.empty(), Optional.empty(), List.of())),
                Optional.empty(), List.of());
    }

    private JsonNode obter(String caminho) throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", "gestor", "senha", UsuariosDeTeste.senhaDe("gestor")));
        String token = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo)).andReturn().getResponse().getContentAsString()).get("token").asText();
        String resposta = mvc.perform(get(caminho).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(resposta);
    }

    private static List<String> codigos(JsonNode avisos) {
        List<String> c = new ArrayList<>();
        avisos.forEach(a -> c.add(a.get("codigo").asText()));
        return c;
    }

    /** A posição sem o aviso TAXA_VENCIDA: tem de ser idêntica à posição sem o financeiro. */
    private static JsonNode semAvisoDeTaxa(JsonNode posicao) {
        ObjectNode copia = posicao.deepCopy();
        ArrayNode avisos = (ArrayNode) copia.get("avisos");
        for (int i = avisos.size() - 1; i >= 0; i--) {
            if ("TAXA_VENCIDA".equals(avisos.get(i).get("codigo").asText())) {
                avisos.remove(i);
            }
        }
        return copia;
    }

    // --- aviso na posição

    @Test
    void fin03_aviso_de_taxa_vencida_sem_alterar_a_posicao() throws Exception {
        JsonNode com = obter("/api/requerimentos/" + FIN03 + "/posicao");
        JsonNode sem = obter("/api/requerimentos/" + SEM_TAXA + "/posicao");
        assertThat(codigos(com.get("avisos"))).containsExactly("TAXA_VENCIDA");
        JsonNode aviso = com.get("avisos").get(0);
        assertThat(aviso.get("texto").asText()).startsWith("Taxa vencida em 10/01/2020.")
                .contains("O SIGA ainda não marca a taxa como vencida");
        assertThat(aviso.get("gravidade").asText()).isEqualTo("ATENCAO");
        // Mesma posição, mesma situação: só muda o número e o aviso.
        ObjectNode a = (ObjectNode) semAvisoDeTaxa(com);
        ObjectNode b = ((ObjectNode) sem).deepCopy();
        for (String numero : List.of("idRequerimento", "numeroRequerimento")) {
            a.remove(numero);
            b.remove(numero);
        }
        assertThat(a).isEqualTo(b);
    }

    @Test
    void fin15_os_dois_avisos_aparecem_e_o_final_nao_muda() throws Exception {
        JsonNode posicao = obter("/api/requerimentos/" + FIN15 + "/posicao");
        assertThat(codigos(posicao.get("avisos"))).containsExactly("FLUXO_ENCERRADO_SEM_STATUS", "TAXA_VENCIDA");
        assertThat(posicao.get("estado").asText()).isEqualTo("ENCERRADO");
        assertThat(posicao.get("situacaoLicenciamento").get("codigo").asText())
                .isEqualTo("AGUARDANDO_PAGAMENTO_DE_TAXAS");
    }

    @Test
    void processo_le_o_financeiro_numa_consulta_em_lote() throws Exception {
        JsonNode lista = obter("/api/processos/9900-2026/posicao");
        Map<Long, List<String>> avisos = new HashMap<>();
        lista.forEach(p -> avisos.put(p.get("idRequerimento").asLong(), codigos(p.get("avisos"))));
        assertThat(avisos.get(FIN03)).contains("TAXA_VENCIDA");
        assertThat(avisos.get(FIN11)).doesNotContain("TAXA_VENCIDA");
        assertThat(avisos.get(SEM_TAXA)).doesNotContain("TAXA_VENCIDA");
        org.mockito.Mockito.verify(lancamentos, org.mockito.Mockito.times(1)).lerEmLote(anyCollection());
        org.mockito.Mockito.verify(lancamentos, org.mockito.Mockito.never()).lerDoRequerimento(anyLong());
    }

    @Test
    void camunda_fora_a_posicao_segue_indisponivel_e_o_aviso_financeiro_aparece() throws Exception {
        JsonNode posicao = obter("/api/requerimentos/" + CAMUNDA_FORA + "/posicao");
        assertThat(posicao.get("estado").asText()).isEqualTo("INDISPONIVEL");
        assertThat(codigos(posicao.get("avisos"))).containsExactly("TAXA_VENCIDA");
    }

    @Test
    void financeiro_fora_a_posicao_sai_igual_a_sem_financeiro() throws Exception {
        when(lancamentos.lerEmLote(anyCollection())).thenThrow(new DataAccessResourceFailureException("fora"));
        JsonNode com = obter("/api/requerimentos/" + FIN03 + "/posicao");
        assertThat(codigos(com.get("avisos"))).isEmpty();
        assertThat(com.get("estado").asText()).isEqualTo("ATIVO");
    }

    // --- marcas na tramitação

    @Test
    void fin11_marcas_nas_passagens_e_etiqueta_da_correcao() throws Exception {
        JsonNode passagens = obter("/api/requerimentos/" + FIN11 + "/tramitacao").get("passagens");
        assertThat(passagens).hasSize(6);
        assertThat(tipos(passagens.get(0))).isEmpty();
        assertThat(tipos(passagens.get(1))).containsExactly("EMITIDA_EM", "NUMERO_DAR");
        assertThat(passagens.get(1).get("marcasFinanceiras").get(1).get("valor").asText()).isEqualTo("FICT-0011");
        assertThat(tipos(passagens.get(2))).containsExactly("VENCE_EM", "PAGA_EM");
        assertThat(tipos(passagens.get(3))).isEmpty();
        assertThat(tipos(passagens.get(4))).containsExactly("EMITIDA_EM", "NUMERO_DAR", "DA_CORRECAO");
        assertThat(tipos(passagens.get(5))).containsExactly("VENCE_EM", "DA_CORRECAO");
        assertThat(passagens.get(5).get("marcasFinanceiras").get(0).get("data").asText()).isEqualTo("2099-12-31");
        assertThat(passagens.get(5).get("marcasFinanceiras").get(1).get("rotulo").asText()).isEqualTo("da correção");
    }

    @Test
    void fin16_plano_b_sem_marcas_e_sem_erro() throws Exception {
        JsonNode tramitacao = obter("/api/requerimentos/" + FIN16 + "/tramitacao");
        assertThat(tramitacao.get("aproximado").asBoolean()).isTrue();
        assertThat(tramitacao.get("passagens")).isEmpty();
        assertThat(tramitacao.toString()).doesNotContain("marcasFinanceiras\":[{");
    }

    @Test
    void camunda_fora_a_tramitacao_segue_indisponivel_sem_marcas() throws Exception {
        JsonNode tramitacao = obter("/api/requerimentos/" + CAMUNDA_FORA + "/tramitacao");
        assertThat(tramitacao.get("estado").asText()).isEqualTo("INDISPONIVEL");
        assertThat(tramitacao.get("passagens")).isEmpty();
    }

    @Test
    void financeiro_fora_a_tramitacao_sai_sem_marcas() throws Exception {
        when(lancamentos.lerDoRequerimento(anyLong())).thenThrow(new DataAccessResourceFailureException("fora"));
        JsonNode passagens = obter("/api/requerimentos/" + FIN11 + "/tramitacao").get("passagens");
        assertThat(passagens).hasSize(6);
        passagens.forEach(p -> assertThat(tipos(p)).isEmpty());
    }

    private static List<String> tipos(JsonNode passagem) {
        List<String> t = new ArrayList<>();
        passagem.get("marcasFinanceiras").forEach(m -> t.add(m.get("tipo").asText()));
        return t;
    }
}
