package br.gov.mt.sema.rastro.financeiro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * "Isento de Taxa" e a cadeia de apoio da data (S9, bloco 4), sem banco: cada elo, data ausente, várias isenções, o
 * pareamento com o fluxo e os casos FIN05, FIN06 e FIN12.
 */
class DataDaIsencaoTest {

    private static final Clock RELOGIO = Clock.fixed(
            LocalDate.of(2026, 3, 20).atTime(10, 0).atZone(ZoneId.of("America/Cuiaba")).toInstant(),
            ZoneId.of("America/Cuiaba"));
    private static final LocalDateTime DIA_4 = LocalDateTime.of(2026, 3, 4, 9, 0);
    private static final LocalDateTime DIA_16 = LocalDateTime.of(2026, 3, 16, 9, 0);

    private static Lancamento isenta(long id, LocalDateTime emissao, String processado) {
        return new Lancamento(id, 1, "ISENTA", true, processado, null, emissao, null, null, null, null, null, null, null);
    }

    private static HistoricoFinanceiro avaliar(List<LocalDateTime> fluxo, Lancamento... lancamentos) {
        return AvaliarSituacao.avaliar(
                ClassificarValidade.classificar(new LeituraDosLancamentos(List.of(lancamentos), false)), RELOGIO, fluxo);
    }

    @Test
    void cadeia_documentada_lancamento_depois_fluxo() {
        assertThat(DataDaIsencao.CADEIA).containsExactly(OrigemDaData.LANCAMENTO, OrigemDaData.FLUXO);
    }

    @Test
    void fin05_isenta_processada_com_data_do_lancamento() {
        HistoricoFinanceiro h = avaliar(List.of(DIA_16), isenta(1, DIA_4, "P"));
        assertThat(h.isencoes()).singleElement().satisfies(r -> {
            assertThat(r.texto()).isEqualTo("Isento de Taxa");
            assertThat(r.data()).contains(LocalDate.of(2026, 3, 4));
            assertThat(r.origemDaData()).as("o lançamento vem antes do fluxo").isEqualTo(OrigemDaData.LANCAMENTO);
            assertThat(r.dataAConfirmar()).isFalse();
            assertThat(r.naoProcessado()).isFalse();
        });
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.ISENTO);
    }

    @Test
    void fin06_processado_n_sem_tratamento_especial_so_nota() {
        HistoricoFinanceiro h = avaliar(List.of(), isenta(1, DIA_4, "N"));
        assertThat(h.isencoes()).singleElement().satisfies(r -> {
            assertThat(r.data()).contains(LocalDate.of(2026, 3, 4));
            assertThat(r.origemDaData()).isEqualTo(OrigemDaData.LANCAMENTO);
            assertThat(r.naoProcessado()).isTrue();
        });
        assertThat(h.resumo()).isEqualTo(ResumoFinanceiro.ISENTO);
        assertThat(h.avisos()).isEmpty();
    }

    @Test
    void fin12_paga_e_depois_isenta_nada_escondido() {
        Lancamento paga = new Lancamento(1, 1, "PAGA", false, "P", "FICT-0012", DIA_4, DIA_4.plusDays(10),
                DIA_4.plusDays(3), null, new BigDecimal("1250.50"), BigDecimal.ZERO, new BigDecimal("35.7286"), null);
        HistoricoFinanceiro h = avaliar(List.of(), paga, isenta(2, DIA_16, "P"));
        assertThat(h.lancamentos()).extracting(LancamentoAvaliado::situacao)
                .containsExactly(SituacaoFinanceira.PAGA, SituacaoFinanceira.ISENTA);
        assertThat(h.isencoes()).singleElement().satisfies(r -> {
            assertThat(r.data()).contains(LocalDate.of(2026, 3, 16));
            assertThat(r.lancamento().ordem()).isEqualTo(2);
        });
    }

    @Test
    void sem_data_no_lancamento_usa_o_fluxo_e_marca_a_confirmar() {
        HistoricoFinanceiro h = avaliar(List.of(DIA_16.withHour(23).withMinute(30)), isenta(1, null, "P"));
        assertThat(h.isencoes()).singleElement().satisfies(r -> {
            assertThat(r.data()).as("23:30 continua no mesmo dia").contains(LocalDate.of(2026, 3, 16));
            assertThat(r.origemDaData()).isEqualTo(OrigemDaData.FLUXO);
            assertThat(r.dataAConfirmar()).isTrue();
        });
    }

    @Test
    void sem_data_em_lugar_nenhum_fica_ausente() {
        HistoricoFinanceiro h = avaliar(List.of(), isenta(1, null, "P"));
        assertThat(h.isencoes()).singleElement().satisfies(r -> {
            assertThat(r.data()).isEmpty();
            assertThat(r.origemDaData()).isEqualTo(OrigemDaData.AUSENTE);
            assertThat(r.dataAConfirmar()).isTrue();
        });
    }

    @Test
    void varias_isencoes_varias_entradas_em_ordem() {
        HistoricoFinanceiro h = avaliar(List.of(DIA_16, DIA_4), isenta(2, null, "P"), isenta(1, DIA_4.minusDays(1), "P"));
        assertThat(h.isencoes()).hasSize(2);
        assertThat(h.isencoes()).extracting(r -> r.data().orElseThrow())
                .containsExactly(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 16));
        assertThat(h.isencoes()).extracting(RegistroDeIsencao::origemDaData)
                .containsExactly(OrigemDaData.LANCAMENTO, OrigemDaData.FLUXO);
    }

    @Test
    void quantidades_diferentes_nao_pareia_com_o_fluxo() {
        HistoricoFinanceiro h = avaliar(List.of(DIA_4), isenta(1, null, "P"), isenta(2, null, "P"));
        assertThat(h.isencoes()).hasSize(2)
                .allSatisfy(r -> assertThat(r.origemDaData()).isEqualTo(OrigemDaData.AUSENTE));
    }

    @Test
    void precisa_do_fluxo_so_com_isencao_sem_data() {
        HistoricoFinanceiro comData = avaliar(List.of(), isenta(1, DIA_4, "P"));
        HistoricoFinanceiro semData = avaliar(List.of(), isenta(1, null, "P"));
        assertThat(DataDaIsencao.precisaDoFluxo(comData.lancamentos())).isFalse();
        assertThat(DataDaIsencao.precisaDoFluxo(semData.lancamentos())).isTrue();
        assertThat(DataDaIsencao.precisaDoFluxo(List.of())).isFalse();
    }

    @Test
    void sem_isencao_sem_registro() {
        Lancamento paga = new Lancamento(1, 1, "PAGA", false, "P", "FICT-0001", DIA_4, null, DIA_4, null, null, null,
                null, null);
        assertThat(avaliar(List.of(DIA_4), paga).isencoes()).isEmpty();
    }

    @Test
    void registro_coerente_entre_data_e_origem() {
        LancamentoAvaliado a = avaliar(List.of(), isenta(1, DIA_4, "P")).lancamentos().getFirst();
        assertThatThrownBy(() -> new RegistroDeIsencao(a, Optional.empty(), OrigemDaData.LANCAMENTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistroDeIsencao(a, Optional.of(LocalDate.of(2026, 3, 4)), OrigemDaData.AUSENTE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
