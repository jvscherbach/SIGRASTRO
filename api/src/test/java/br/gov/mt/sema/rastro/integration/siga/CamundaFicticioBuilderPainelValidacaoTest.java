package br.gov.mt.sema.rastro.integration.siga;

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

/** O construtor recusa a paralela e a tarefa não mapeada incoerentes, e os relógios do painel têm o fuso certo. Sem Docker. */
class CamundaFicticioBuilderPainelValidacaoTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CamundaFicticioBuilder camunda = new CamundaFicticioBuilder(jdbc, CatalogoDeEtapasDeTeste.padrao());

    @Test
    void paralelaExigeEtapaPrincipalEOutroRamo() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).comTarefaParalela("taskCorrecaoRequerimento", null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).naEtapa("taskRevisarParecerTecnico")
                .comTarefaParalela("taskRevisarParecerTecnico", null)).isInstanceOf(IllegalStateException.class);
        // mesmo ramo (principal): não é paralela de outro ramo
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).naEtapa("taskRevisarParecerTecnico")
                .comTarefaParalela("taskAtribuirRequerimento", null)).isInstanceOf(IllegalStateException.class);
        // multi-instância não vale como paralela
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).naEtapa("taskRevisarParecerTecnico")
                .comTarefaParalela("taskAnalisarRequerimento", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(camunda.processo(Fluxo.HIDRICO, 1).naEtapa("taskAnalisarRequerimento")
                .comTarefaParalela("taskCorrecaoRequerimento", null)).isNotNull();
        verifyNoInteractions(jdbc);
    }

    @Test
    void tarefaNaoMapeadaExigeIdFicticioForaDoCatalogo() {
        var processo = camunda.processo(Fluxo.HIDRICO, 1);
        assertThatThrownBy(() -> processo.comTarefaNaoMapeada("taskAnalisarRequerimento", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processo.comTarefaNaoMapeada("taskQualquer", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(processo.comTarefaNaoMapeada(CenariosFicticiosDaS10.TAREFA_NAO_MAPEADA, null)).isSameAs(processo);
        verifyNoInteractions(jdbc);
    }

    @Test
    void semInstanciaNaoAceitaTarefaExtra() {
        var processo = camunda.processo(Fluxo.HIDRICO, 1).semInstancia()
                .comTarefaNaoMapeada(CenariosFicticiosDaS10.TAREFA_NAO_MAPEADA, null);
        assertThatThrownBy(processo::gravar).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void relogiosDoPainelUsamCuiabaEOAnoNaoMudaNaViradaUtc() {
        assertThat(LocalDate.now(CenariosFicticiosDaS10.RELOGIO)).isEqualTo(CenariosFicticiosDaS10.HOJE);
        assertThat(CenariosFicticiosDaS10.RELOGIO.getZone().getId()).isEqualTo("America/Cuiaba");
        // Em UTC já é 2027, em Cuiabá ainda é 2026.
        assertThat(LocalDate.now(CenariosFicticiosDaS10.RELOGIO_VIRADA_PARA_2027_NO_UTC).getYear()).isEqualTo(2026);
        assertThat(ZonedDateTime.now(CenariosFicticiosDaS10.RELOGIO_VIRADA_PARA_2027_NO_UTC)
                .withZoneSameInstant(ZoneOffset.UTC).getYear()).isEqualTo(2027);
        // Em UTC já é 2026, em Cuiabá ainda é 2025.
        assertThat(LocalDate.now(CenariosFicticiosDaS10.RELOGIO_VIRADA_PARA_2026_NO_UTC).getYear()).isEqualTo(2025);
        assertThat(ZonedDateTime.now(CenariosFicticiosDaS10.RELOGIO_VIRADA_PARA_2026_NO_UTC)
                .withZoneSameInstant(ZoneOffset.UTC).getYear()).isEqualTo(2026);
    }

    @Test
    void asConstantesEsperadasFecham() {
        int protocolados = 0;
        for (int n : CenariosFicticiosDaS10.PROTOCOLADOS_POR_MES_SEM_VOLUME) {
            protocolados += n;
        }
        int concluidos = 0;
        for (int n : CenariosFicticiosDaS10.CONCLUIDOS_POR_MES) {
            concluidos += n;
        }
        assertThat(protocolados).isEqualTo(CenariosFicticiosDaS10.PROTOCOLADOS_SEM_VOLUME);
        assertThat(concluidos).isEqualTo(CenariosFicticiosDaS10.CONCLUIDOS);
        assertThat(CenariosFicticiosDaS10.idsSemVolume()).hasSize(CenariosFicticiosDaS10.PROTOCOLADOS_SEM_VOLUME + 2)
                .doesNotHaveDuplicates();
        assertThat(CenariosFicticiosDaS10.PN16_POR_MES * 12).isEqualTo(CenariosFicticiosDaS10.PN16_TOTAL);
        assertThat(CenariosFicticiosDaS10.PN16_PRIMEIRO + CenariosFicticiosDaS10.PN16_TOTAL - 1).isLessThan(13000);
    }
}
