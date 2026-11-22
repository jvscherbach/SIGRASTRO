package br.gov.mt.sema.rastro.financeiro.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.application.port.PassagensDeIsencao;
import br.gov.mt.sema.rastro.financeiro.domain.Lancamento;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import br.gov.mt.sema.rastro.financeiro.domain.OrigemDaData;
import br.gov.mt.sema.rastro.financeiro.domain.RegistroDeIsencao;
import org.junit.jupiter.api.Test;

/** S9, bloco 4: a tramitação só é lida quando há isenção sem data no lançamento, e nunca no lote. */
class LerLancamentosIsencaoTest {

    private static final LocalDateTime EMISSAO = LocalDateTime.of(2026, 3, 4, 9, 0);
    private static final LocalDateTime ENTRADA_NO_FLUXO = LocalDateTime.of(2026, 3, 4, 10, 0);

    private final AtomicInteger leiturasDoFluxo = new AtomicInteger();
    private final PassagensDeIsencao fluxo = id -> {
        leiturasDoFluxo.incrementAndGet();
        return List.of(ENTRADA_NO_FLUXO);
    };

    private static Lancamento isenta(LocalDateTime emissao) {
        return new Lancamento(1, 10, "ISENTA", true, "P", null, emissao, null, null, null, null, null, null, null);
    }

    private static LancamentosDoRequerimento porta(Lancamento... lancamentos) {
        return new LancamentosDoRequerimento() {
            @Override
            public LeituraDosLancamentos lerDoRequerimento(long idRequerimento) {
                return new LeituraDosLancamentos(List.of(lancamentos), false);
            }

            @Override
            public Map<Long, LeituraDosLancamentos> lerEmLote(Collection<Long> ids) {
                return Map.of(10L, new LeituraDosLancamentos(List.of(lancamentos), false));
            }
        };
    }

    @Test
    void isencao_com_data_no_lancamento_nao_le_a_tramitacao() {
        var h = new LerLancamentos(porta(isenta(EMISSAO)), Clock.systemUTC(), fluxo).historico(10);
        assertThat(leiturasDoFluxo).hasValue(0);
        assertThat(h.isencoes()).singleElement().extracting(RegistroDeIsencao::origemDaData)
                .isEqualTo(OrigemDaData.LANCAMENTO);
    }

    @Test
    void isencao_sem_data_no_lancamento_usa_a_entrada_no_fluxo() {
        var h = new LerLancamentos(porta(isenta(null)), Clock.systemUTC(), fluxo).historico(10);
        assertThat(leiturasDoFluxo).hasValue(1);
        assertThat(h.isencoes()).singleElement().satisfies(r -> {
            assertThat(r.origemDaData()).isEqualTo(OrigemDaData.FLUXO);
            assertThat(r.data()).contains(LocalDate.of(2026, 3, 4));
            assertThat(r.dataAConfirmar()).isTrue();
        });
    }

    @Test
    void sem_isencao_nao_le_a_tramitacao() {
        Lancamento paga = new Lancamento(2, 10, "PAGA", false, "P", "FICT-0001", EMISSAO, null, EMISSAO, null, null,
                null, null, null);
        var h = new LerLancamentos(porta(paga), Clock.systemUTC(), fluxo).historico(10);
        assertThat(leiturasDoFluxo).hasValue(0);
        assertThat(h.isencoes()).isEmpty();
    }

    @Test
    void lote_nunca_le_a_tramitacao() {
        var lote = new LerLancamentos(porta(isenta(null)), Clock.systemUTC(), fluxo).historicoEmLote(List.of(10L));
        assertThat(leiturasDoFluxo).hasValue(0);
        assertThat(lote.get(10L).isencoes()).singleElement().extracting(RegistroDeIsencao::origemDaData)
                .isEqualTo(OrigemDaData.AUSENTE);
    }
}
