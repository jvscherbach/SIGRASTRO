package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.dias;
import static org.assertj.core.api.Assertions.assertThat;
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

    // --- caminho histórico (S8)

    private static final String CADASTRO = "taskCadastrarRequerimento";
    private static final String CONFERENCIA = "taskConferirDocumentosApresentados";
    private static final String ANALISE = "taskAnalisarRequerimento";

    @Test
    void passoPrecisaDeInicioEFimComOFimDepoisDoInicio() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passou(CADASTRO, dias(1), dias(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passou(CADASTRO, null, dias(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passou(CADASTRO, dias(0), null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void passouRecusaIdForaDoCatalogoEMultiInstanciaEEmissao() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passou("taskInexistente", dias(0), dias(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passou(ANALISE, dias(0), dias(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("passouEmCiclo");
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passou("taskEmitirPortarias", dias(0), dias(1)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void cicloSoEmEtapaMultiInstanciaEEsperaSoEmEtapaDeEspera() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passouEmCiclo(CADASTRO, dias(0), dias(1), "00000000005"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passouEmCiclo(ANALISE, dias(0), dias(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passouPelaEspera(CADASTRO, dias(0), dias(1)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void voltaExigeEtapaJaPercorridaEAlgoDepoisDela() {
        // nunca passou por ela
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).voltouPara(CADASTRO, dias(0), dias(1)))
                .isInstanceOf(IllegalStateException.class);
        // passou, mas nada veio depois: repetição imediata não é volta
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passou(CADASTRO, dias(0), dias(1))
                .voltouPara(CADASTRO, dias(1), dias(2)))
                .isInstanceOf(IllegalStateException.class);
        // com outra etapa no meio, é volta
        camunda.processo(Fluxo.HIDRICO, 1).passou(CADASTRO, dias(0), dias(1)).passou(CONFERENCIA, dias(1), dias(2))
                .voltouPara(CADASTRO, dias(2), dias(3));
        verifyNoInteractions(jdbc);
    }

    @Test
    void variavelSoDaListaBrancaEValorSemCaraDeCpf() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).variavel("cpfDoRequerente", "x", dias(0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lista branca");
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).variavel("statusConferencia", "00000000091", dias(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).variavel("statusConferencia", null, dias(0)))
                .isInstanceOf(IllegalArgumentException.class);
        for (String nome : CamundaFicticioBuilder.VARIAVEIS_PERMITIDAS) {
            camunda.processo(Fluxo.HIDRICO, 1).variavel(nome, "valor", dias(0));
        }
        assertThat(CamundaFicticioBuilder.VARIAVEIS_PERMITIDAS).containsExactlyInAnyOrder("decisaoValidacaoParecerTecnico",
                "revisarRequerimento", "decisaoVerificarRevisao", "isRevisaoAnalise", "isRevisaoParecer", "statusConferencia");
        verifyNoInteractions(jdbc);
    }

    @Test
    void atividadeFicticiaPrecisaTerIdEvidenteEDesconhecidoDoCatalogo() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).atividadeFicticia("Activity_ReportKafka", "serviceTask",
                dias(0), dias(0))).isInstanceOf(IllegalArgumentException.class);
        // id evidente e desconhecido do catálogo é aceito
        camunda.processo(Fluxo.HIDRICO, 1).atividadeFicticia("Activity_FicticioAlgo", "serviceTask", dias(0), dias(0));
        // id do catálogo é recusado mesmo com "Ficticio" no meio: aqui o próprio id fictício do ruído conhecido
        assertThat(CamundaFicticioBuilder.REPORT_KAFKA_FICTICIO).contains("Ficticio");
        verifyNoInteractions(jdbc);
    }

    @Test
    void scriptRepetidoSoEmEtapaAutomaticaComAoMenosDuasVezes() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).scriptRepetido(CADASTRO, 4, dias(0), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).scriptRepetido("taskEmitirTaxaSefaz", 1, dias(0), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).scriptRepetido("taskEmitirTaxaSefaz", 4, dias(0), 0))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void suporteSoNoModeloQueTemTarefaDeSuporte() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.LAC, 1).passouPeloSuporte(dias(0), dias(1)))
                .isInstanceOf(IllegalStateException.class);
        camunda.processo(Fluxo.HIDRICO, 1).passouPeloSuporte(dias(0), dias(1));
        verifyNoInteractions(jdbc);
    }

    @Test
    void caminhoENaoPercorrendoNaoSeMisturam() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).percorrendo(CADASTRO)
                .passou(CONFERENCIA, dias(0), dias(1)).naEtapa(ANALISE).desde(dias(2)).gravar())
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void caminhoNaoPodeTerminarDepoisDaEtapaAtivaNemTerVariavelSemCaminho() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passou(CADASTRO, dias(0), dias(5))
                .naEtapa(CONFERENCIA).desde(dias(2)).gravar()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).variavel("statusConferencia", "isento", dias(0))
                .naEtapa(CONFERENCIA).gravar()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void semInstanciaNaoTemCaminhoNemVariavelNemComplementacao() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).semInstancia().passou(CADASTRO, dias(0), dias(1)).gravar())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).semInstancia()
                .complementacaoConcluida(9, dias(0), dias(1)).gravar()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void emissaoComDevolucaoPrecisaDeFilhasDoCatalogo() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1)
                .passouPelaEmissao(dias(0), dias(1), "procGestaoComplementacao")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passouPelaEmissao(dias(0), dias(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.processo(Fluxo.LAC, 1).passouPelaEmissao(dias(0), dias(1), "procSurhEmissaoPortaria"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void isencaoConfirmadaComPassagemPeloGatewayContinuaRecusadaComCaminhoHistorico() {
        assertThatThrownBy(() -> camunda.processo(Fluxo.HIDRICO, 1).passouPelaEspera("gtwAguardarPagamento", dias(0), dias(1))
                .naEtapa("taskGerarNumeroProtocolo").desde(dias(2)).gravar()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }
}
