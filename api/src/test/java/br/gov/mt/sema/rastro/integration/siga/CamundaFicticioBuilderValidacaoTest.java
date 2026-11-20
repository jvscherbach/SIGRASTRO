package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** O construtor recusa, antes de gravar qualquer linha, cenário com id fora do catálogo ou incoerente. Sem Docker. */
class CamundaFicticioBuilderValidacaoTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CamundaFicticioBuilder camunda = new CamundaFicticioBuilder(jdbc, CatalogoDeEtapasDeTeste.padrao());

    @Test
    void idTecnicoForaDoCatalogoEhRecusado() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).naEtapa("taskInexistente"))
                .isInstanceOf(IllegalArgumentException.class);
        // id do LAC não vale no Hídrico, e vice-versa
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).naEtapa("taskEnviarTermoAdesao"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.LAC, 1).percorrendo("gtwAguardarPagamento"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void idIgnoradoNaoEhEtapa() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).naEtapa("Activity_0miwhxt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emissaoSoPorFilhasEFilhaPrecisaEstarNoCatalogo() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).naEtapa("taskEmitirPortarias"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).comFilhas("procGestaoComplementacao"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.LAC, 1).comFilhas("procSurhEmissaoPortaria"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void finalPrecisaSerFinalDoModelo() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).encerradoEm("taskCadastrarRequerimento"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).encerradoEm("evtProcessoConcluido"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isencaoComPassagemPeloGatewayDePagamentoEhIncoerente() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1)
                .percorrendo("gtwAguardarPagamento").naEtapa("taskGerarNumeroProtocolo").gravar())
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void multiInstanciaSoEmEtapaMultiInstanciaEConcluidosMenorQueOTotal() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1)
                .naEtapa("taskRevisarParecerTecnico").comAnalistas(2).gravar())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1)
                .naEtapa("taskAnalisarRequerimento").comAnalistas(2).concluidos(2).gravar())
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void cenarioSemEtapaAtivaEEncerradoComEtapaSaoRecusados() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).gravar()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1)
                .naEtapa("taskCadastrarRequerimento").encerradoEm("Event_1wiuc48").gravar())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1)
                .semInstancia().naEtapa("taskCadastrarRequerimento").gravar())
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void trifasicoNaoTemModeloNoCatalogo() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.TRIFASICO, 1).naEtapa("taskCadastrarRequerimento"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void servidorFicticioSoDe1A12() {
        assertThatThrownBy(() -> CamundaFicticioBuilder.cpfDoServidor(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CamundaFicticioBuilder.cpfDoServidor(13)).isInstanceOf(IllegalArgumentException.class);
    }
}
