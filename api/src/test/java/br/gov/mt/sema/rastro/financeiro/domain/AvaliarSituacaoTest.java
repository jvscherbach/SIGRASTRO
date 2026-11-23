package br.gov.mt.sema.rastro.financeiro.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Situação financeira, taxa vencida por data e ciclos (S9, bloco 3), sem banco. "Hoje" = 2026-03-20 em Cuiabá, como em
 * {@code CenariosFicticiosDaS9}; os casos FIN01 a FIN04, FIN11 e FIN12 reproduzem os lançamentos desses cenários.
 */
class AvaliarSituacaoTest {

    private static final ZoneId CUIABA = ZoneId.of("America/Cuiaba");
    private static final LocalDate HOJE = LocalDate.of(2026, 3, 20);
    private static final Clock RELOGIO = Clock.fixed(HOJE.atTime(10, 0).atZone(CUIABA).toInstant(), CUIABA);
    private static final LocalDateTime EMISSAO = LocalDateTime.of(2026, 3, 4, 9, 0);

    private static long proximoId = 1;

    private static Lancamento l(String status, String dar, LocalDateTime emissao, LocalDateTime vencimento,
            LocalDateTime pagamento, LocalDateTime fim) {
        return new Lancamento(proximoId++, 1, status, false, "P", dar, emissao, vencimento, pagamento, fim,
                new BigDecimal("980.00"), new BigDecimal("0.00"), new BigDecimal("28.0000"), null);
    }

    private static Lancamento aguardando(LocalDate vencimento) {
        return l("AGUARDANDO_PAGAMENTO", "FICT-0002", EMISSAO, vencimento.atStartOfDay(), null, null);
    }

    private static Lancamento paga() {
        return l("PAGA", "FICT-0001", EMISSAO, LocalDate.of(2026, 3, 14).atStartOfDay(), EMISSAO.plusDays(3), null);
    }

    private static HistoricoFinanceiro avaliar(Clock relogio, Lancamento... lancamentos) {
        return AvaliarSituacao.avaliar(
                ClassificarValidade.classificar(new LeituraDosLancamentos(List.of(lancamentos), false)), relogio);
    }

    private static HistoricoFinanceiro avaliar(Lancamento... lancamentos) {
        return avaliar(RELOGIO, lancamentos);
    }

    private static List<CodigoDoAvisoFinanceiro> codigos(HistoricoFinanceiro h) {
        return h.avisos().stream().map(AvisoFinanceiro::codigo).toList();
    }

    @Test
    void fin01_paga_quitado() {
        HistoricoFinanceiro h = avaliar(paga());
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.QUITADO);
        assertThat(h.lancamentos()).singleElement().satisfies(a -> {
            assertThat(a.situacao()).isEqualTo(SituacaoFinanceira.PAGA);
            assertThat(a.rotulo()).contains("1ª taxa");
            assertThat(a.ordem()).isEqualTo(1);
            assertThat(a.vencida()).isFalse();
            assertThat(a.pagaEm()).contains(LocalDate.of(2026, 3, 7));
            assertThat(a.emitidaEm()).contains(LocalDate.of(2026, 3, 4));
        });
        assertThat(h.avisos()).isEmpty();
    }

    @Test
    void fin02_no_prazo_em_aberto() {
        HistoricoFinanceiro h = avaliar(aguardando(HOJE.plusDays(1)));
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.EM_ABERTO);
        assertThat(h.lancamentos().getFirst().vencida()).isFalse();
        assertThat(h.avisos()).isEmpty();
    }

    @Test
    void fin03_vencida_por_data_sem_trocar_a_situacao() {
        HistoricoFinanceiro h = avaliar(aguardando(HOJE.minusDays(1)));
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.VENCIDO);
        assertThat(h.lancamentos()).singleElement().satisfies(a -> {
            assertThat(a.situacao()).isEqualTo(SituacaoFinanceira.AGUARDANDO_PAGAMENTO);
            assertThat(a.vencida()).isTrue();
            assertThat(a.vencidaPorData()).isTrue();
        });
        assertThat(h.avisos()).singleElement().satisfies(a -> {
            assertThat(a.codigo()).isEqualTo(CodigoDoAvisoFinanceiro.TAXA_VENCIDA);
            assertThat(a.texto()).isEqualTo("Taxa vencida em 19/03/2026.");
            assertThat(a.gravidade()).isEqualTo(AvisoFinanceiro.Gravidade.ATENCAO);
        });
    }

    @Test
    void vencimento_hoje_ainda_nao_e_vencida() {
        HistoricoFinanceiro h = avaliar(aguardando(HOJE));
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.EM_ABERTO);
        assertThat(h.avisos()).isEmpty();
    }

    @Test
    void fin04_vencida_pelo_siga_sem_aviso_acrescentado() {
        HistoricoFinanceiro h = avaliar(l("VENCIDA", "FICT-0004", EMISSAO, LocalDate.of(2026, 3, 14).atStartOfDay(),
                null, null));
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.VENCIDO);
        assertThat(h.lancamentos()).singleElement().satisfies(a -> {
            assertThat(a.situacao()).isEqualTo(SituacaoFinanceira.VENCIDA);
            assertThat(a.vencida()).isTrue();
            assertThat(a.vencidaPorData()).isFalse();
        });
        assertThat(codigos(h)).doesNotContain(CodigoDoAvisoFinanceiro.TAXA_VENCIDA);
    }

    @Test
    void aviso_some_quando_a_taxa_e_paga() {
        Lancamento pagaDepois = l("PAGA", "FICT-0003", EMISSAO, HOJE.minusDays(1).atStartOfDay(),
                HOJE.atTime(8, 0), null);
        HistoricoFinanceiro h = avaliar(pagaDepois);
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.QUITADO);
        assertThat(h.avisos()).isEmpty();
    }

    @Test
    void aguardando_com_data_de_pagamento_nao_e_dada_como_vencida() {
        Lancamento pagoSemStatus = l("AGUARDANDO_PAGAMENTO", "FICT-0003", EMISSAO, HOJE.minusDays(1).atStartOfDay(),
                HOJE.atTime(8, 0), null);
        assertThat(avaliar(pagoSemStatus).lancamentos().getFirst().vencida()).isFalse();
    }

    @Test
    void encerrado_nao_vence_nem_fica_em_aberto() {
        Lancamento encerrado = l("AGUARDANDO_PAGAMENTO", "FICT-0003", EMISSAO, HOJE.minusDays(1).atStartOfDay(), null,
                EMISSAO.plusDays(1));
        Lancamento vencidaEncerrada = l("VENCIDA", "FICT-0004", EMISSAO, HOJE.minusDays(5).atStartOfDay(), null,
                EMISSAO.plusDays(1));
        HistoricoFinanceiro h = avaliar(encerrado, vencidaEncerrada);
        assertThat(h.lancamentos()).allSatisfy(a -> assertThat(a.vencida()).isFalse());
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.SEM_TAXA);
        assertThat(h.avisos()).isEmpty();
    }

    @Test
    void inconsistente_nunca_e_dado_como_vencido_por_data() {
        Lancamento semDar = l("AGUARDANDO_PAGAMENTO", null, EMISSAO, HOJE.minusDays(3).atStartOfDay(), null, null);
        HistoricoFinanceiro h = avaliar(semDar);
        assertThat(h.lancamentos().getFirst().vencida()).isFalse();
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.EM_ABERTO);
        assertThat(codigos(h)).containsExactly(CodigoDoAvisoFinanceiro.LANCAMENTO_INCONSISTENTE);
    }

    @Test
    void sem_data_de_vencimento_nao_se_afirma_vencida() {
        HistoricoFinanceiro h = avaliar(l("AGUARDANDO_PAGAMENTO", "FICT-0002", EMISSAO, null, null, null));
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.EM_ABERTO);
        assertThat(h.avisos()).isEmpty();
    }

    @Test
    void fin11_inicial_paga_e_correcao_aguardando() {
        HistoricoFinanceiro h = avaliar(paga(), l("AGUARDANDO_PAGAMENTO", "FICT-0111", EMISSAO.plusDays(10),
                HOJE.plusDays(10).atStartOfDay(), null, null));
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.EM_ABERTO);
        assertThat(h.lancamentos()).extracting(a -> a.rotulo().orElseThrow()).containsExactly("1ª taxa", "2ª taxa");
        assertThat(h.lancamentos()).extracting(LancamentoAvaliado::situacao)
                .containsExactly(SituacaoFinanceira.PAGA, SituacaoFinanceira.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void fin12_paga_e_depois_isenta_aparecem_os_dois_em_ordem() {
        Lancamento isenta = new Lancamento(proximoId++, 1, "ISENTA", true, "P", null, EMISSAO.plusDays(10), null, null,
                null, null, null, null, null);
        HistoricoFinanceiro h = avaliar(isenta, paga());
        assertThat(h.lancamentos()).extracting(LancamentoAvaliado::situacao)
                .containsExactly(SituacaoFinanceira.PAGA, SituacaoFinanceira.ISENTA);
        assertThat(h.lancamentos()).extracting(LancamentoAvaliado::ordem).containsExactly(1, 2);
        assertThat(h.lancamentos().get(0).rotulo()).contains("1ª taxa");
        assertThat(h.lancamentos().get(1).rotulo()).isEmpty();
        assertThat(h.lancamentos().get(1).valor()).as("valor ausente é não informado, nunca zero").isEmpty();
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.QUITADO);
    }

    @Test
    void valores_em_big_decimal_como_gravados() {
        LancamentoAvaliado a = avaliar(paga()).lancamentos().getFirst();
        assertThat(a.valor()).contains(new BigDecimal("980.00"));
        assertThat(a.desconto()).contains(new BigDecimal("0.00"));
        assertThat(a.upf()).contains(new BigDecimal("28.0000"));
        assertThat(a.numeroDar()).contains("FICT-0001");
    }

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource({
            "VENCIDO_POR_DATA, EM_ABERTO, VENCIDO",
            "VENCIDA_SIGA, PAGA, VENCIDO",
            "EM_ABERTO, PAGA, EM_ABERTO",
            "EMITINDO, ISENTA, EM_ABERTO",
            "PAGA, ISENTA, QUITADO",
            "ISENTA, CANCELADA, ISENTO",
            "CANCELADA, CANCELADA, SEM_TAXA",
            "VENCIDO_POR_DATA, ISENTA, VENCIDO"})
    void precedencia_do_resumo(String a, String b, ResumoFinanceiro esperado) {
        assertThat(avaliar(caso(a), caso(b)).resumo()).isEqualTo(esperado);
        assertThat(avaliar(caso(b), caso(a)).resumo()).as("a ordem não muda o resumo").isEqualTo(esperado);
    }

    private static Lancamento caso(String nome) {
        return switch (nome) {
            case "VENCIDO_POR_DATA" -> aguardando(HOJE.minusDays(2));
            case "VENCIDA_SIGA" -> l("VENCIDA", "FICT-0004", EMISSAO, HOJE.minusDays(9).atStartOfDay(), null, null);
            case "EM_ABERTO" -> aguardando(HOJE.plusDays(2));
            case "EMITINDO" -> l("AGUARDANDO_EMISSAO", null, null, null, null, null);
            case "PAGA" -> paga();
            case "ISENTA" -> l("ISENTA", null, EMISSAO, null, null, null);
            case "CANCELADA" -> l("CANCELADA", "FICT-0008", EMISSAO, null, null, EMISSAO.plusDays(1));
            default -> throw new IllegalArgumentException(nome);
        };
    }

    @Test
    void sem_lancamento_e_sem_taxa() {
        HistoricoFinanceiro h = AvaliarSituacao.avaliar(LancamentosClassificados.NENHUM, RELOGIO);
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.SEM_TAXA);
        assertThat(h.lancamentos()).isEmpty();
    }

    @Test
    void so_status_desconhecido_e_indeterminado() {
        HistoricoFinanceiro h = avaliar(l("ESTORNADA", "FICT-0009", EMISSAO, null, null, null));
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.INDETERMINADO);
        assertThat(h.lancamentos().getFirst().situacao()).isEqualTo(SituacaoFinanceira.DESCONHECIDA);
        assertThat(h.lancamentos().getFirst().rotulo()).isEmpty();
        assertThat(avaliar(l("ESTORNADA", "FICT-0009", EMISSAO, null, null, null), paga()).resumo())
                .as("com um conhecido, ele decide").isEqualTo(ResumoFinanceiro.QUITADO);
    }

    @Test
    void so_ignorados_e_sem_taxa() {
        HistoricoFinanceiro h = avaliar(l("CANCELADA", null, EMISSAO, HOJE.minusDays(3).atStartOfDay(), null, null),
                new Lancamento(proximoId++, 1, "AGUARDANDO_EMISSAO", true, "P", null, null, null, null, null, null, null,
                        null, null));
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.SEM_TAXA);
        assertThat(h.lancamentos()).isEmpty();
        assertThat(h.classificados().ignorados()).hasSize(2);
    }

    /** Propriedade: nenhum lançamento ignorado entra no resumo nem na lista, qualquer que seja a mistura. */
    @RepeatedTest(50)
    void ignorados_nunca_mudam_o_resumo() {
        Random sorteio = new Random(proximoId * 7919);
        List<Lancamento> validos = new ArrayList<>();
        String[] nomes = {"VENCIDO_POR_DATA", "VENCIDA_SIGA", "EM_ABERTO", "EMITINDO", "PAGA", "ISENTA", "CANCELADA"};
        for (int i = sorteio.nextInt(4); i > 0; i--) {
            validos.add(caso(nomes[sorteio.nextInt(nomes.length)]));
        }
        List<Lancamento> comIgnorados = new ArrayList<>(validos);
        for (int i = 1 + sorteio.nextInt(5); i > 0; i--) {
            comIgnorados.add(sorteio.nextBoolean()
                    ? l("CANCELADA", null, EMISSAO, HOJE.minusDays(10).atStartOfDay(), null, null)
                    : new Lancamento(proximoId++, 1, "AGUARDANDO_EMISSAO", true, "P", null, null, null, null, null, null,
                            null, null, null));
        }
        HistoricoFinanceiro sem = avaliar(validos.toArray(Lancamento[]::new));
        HistoricoFinanceiro com = avaliar(comIgnorados.toArray(Lancamento[]::new));
        assertThat(com.resumo()).isEqualTo(sem.resumo());
        assertThat(com.lancamentos()).hasSameSizeAs(sem.lancamentos());
        assertThat(com.avisos()).isEqualTo(sem.avisos());
    }

    // ---- data, fuso e virada do dia ----

    @ParameterizedTest(name = "relógio em {0}, fuso do relógio {1}: hoje = {2}")
    @CsvSource({
            "2026-03-21T01:30:00Z, UTC, 2026-03-20",
            "2026-03-21T01:30:00Z, America/Cuiaba, 2026-03-20",
            "2026-03-21T03:59:59Z, Asia/Tokyo, 2026-03-20",
            "2026-03-21T04:00:00Z, UTC, 2026-03-21",
            "2026-03-20T04:00:00Z, America/Cuiaba, 2026-03-20",
            "2026-03-20T03:59:59Z, America/Cuiaba, 2026-03-19"})
    void hoje_e_a_data_de_cuiaba_qualquer_que_seja_o_fuso_do_relogio(String instante, String fuso, LocalDate hoje) {
        assertThat(CalendarioDeCuiaba.hoje(Clock.fixed(Instant.parse(instante), ZoneId.of(fuso)))).isEqualTo(hoje);
    }

    @Test
    void na_virada_utc_ainda_e_hoje_em_cuiaba() {
        Clock viradaUtc = Clock.fixed(Instant.parse("2026-03-21T01:30:00Z"), ZoneOffset.UTC);
        assertThat(avaliar(viradaUtc, aguardando(HOJE)).resumo()).as("vence hoje (20), ainda não vencida")
                .isEqualTo(ResumoFinanceiro.EM_ABERTO);
        assertThat(avaliar(viradaUtc, aguardando(HOJE.minusDays(1))).resumo()).isEqualTo(ResumoFinanceiro.VENCIDO);
        Clock meiaNoiteEmCuiaba = Clock.fixed(Instant.parse("2026-03-21T04:00:00Z"), ZoneOffset.UTC);
        assertThat(avaliar(meiaNoiteEmCuiaba, aguardando(HOJE)).resumo()).as("virou o dia em Cuiabá")
                .isEqualTo(ResumoFinanceiro.VENCIDO);
    }

    @Test
    void vencimento_com_hora_nao_desloca_o_dia() {
        Lancamento fimDeOntem = l("AGUARDANDO_PAGAMENTO", "FICT-0002", EMISSAO, HOJE.minusDays(1).atTime(23, 59, 59),
                null, null);
        Lancamento inicioDeHoje = l("AGUARDANDO_PAGAMENTO", "FICT-0002", EMISSAO, HOJE.atStartOfDay(), null, null);
        Lancamento fimDeHoje = l("AGUARDANDO_PAGAMENTO", "FICT-0002", EMISSAO, HOJE.atTime(23, 30), null, null);
        assertThat(avaliar(fimDeOntem).avisos()).extracting(AvisoFinanceiro::texto)
                .containsExactly("Taxa vencida em 19/03/2026.");
        assertThat(avaliar(inicioDeHoje).resumo()).isEqualTo(ResumoFinanceiro.EM_ABERTO);
        assertThat(avaliar(fimDeHoje).resumo()).isEqualTo(ResumoFinanceiro.EM_ABERTO);
        assertThat(avaliar(fimDeHoje).lancamentos().getFirst().venceEm()).contains(HOJE);
    }
}
