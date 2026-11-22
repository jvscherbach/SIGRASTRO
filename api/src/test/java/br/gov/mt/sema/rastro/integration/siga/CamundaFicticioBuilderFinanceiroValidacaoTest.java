package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.StatusDaGuia.PAGA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** A API de lançamentos do construtor recusa dado que pareça real, antes de gravar qualquer linha. Sem Docker. */
class CamundaFicticioBuilderFinanceiroValidacaoTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CamundaFicticioBuilder camunda = new CamundaFicticioBuilder(jdbc, CatalogoDeEtapasDeTeste.padrao());

    @Test
    void darQueNaoEhFicticioEhRecusado() {
        var lancamento = camunda.processo(Fluxo.HIDRICO, 1).comLancamento(PAGA);
        assertThatThrownBy(() -> lancamento.comDar("123456789")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lancamento.comDar(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(lancamento.comDar("FICT-0001")).isSameAs(lancamento);
        verifyNoInteractions(jdbc);
    }

    @Test
    void statusProcessadoSoAceitaNouP() {
        var lancamento = camunda.processo(Fluxo.HIDRICO, 1).comLancamento(PAGA);
        assertThatThrownBy(() -> lancamento.processado('X')).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void noMaximo99LancamentosPorRequerimento() {
        var processo = camunda.processo(Fluxo.HIDRICO, 1);
        for (int i = 0; i < 99; i++) {
            processo.comLancamento(PAGA);
        }
        assertThatThrownBy(() -> processo.comLancamento(PAGA)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ofusoDoRelogioDeTesteEhCuiabaEOdiaNaoMudaNaViradaUtc() {
        assertThat(CenariosFicticiosDaS9.RELOGIO.getZone().getId()).isEqualTo("America/Cuiaba");
        assertThat(LocalDate.now(CenariosFicticiosDaS9.RELOGIO)).isEqualTo(CenariosFicticiosDaS9.HOJE);
        // Na virada UTC (01:30Z do dia seguinte) em Cuiabá ainda é "hoje"; em UTC já é o dia seguinte.
        assertThat(LocalDate.now(CenariosFicticiosDaS9.RELOGIO_NA_VIRADA_UTC)).isEqualTo(CenariosFicticiosDaS9.HOJE);
        assertThat(ZonedDateTime.now(CenariosFicticiosDaS9.RELOGIO_NA_VIRADA_UTC).withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDate()).isEqualTo(CenariosFicticiosDaS9.HOJE.plusDays(1));
    }

    @Test
    void ontemHojeEAmanhaSaoRelativosAHoje() {
        assertThat(CenariosFicticiosDaS9.ONTEM).isEqualTo(CenariosFicticiosDaS9.HOJE.minusDays(1));
        assertThat(CenariosFicticiosDaS9.AMANHA).isEqualTo(CenariosFicticiosDaS9.HOJE.plusDays(1));
    }
}
