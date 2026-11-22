package br.gov.mt.sema.rastro.tramitacao.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.AtividadeBruta.MotivoDaTarefa;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido.ClassificacaoDaTaxa;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import org.junit.jupiter.api.Test;

/**
 * Regras de borda da reconstrução (S8, bloco 3), sem banco: caminhos montados à mão. A sequência de cada cenário das
 * fixtures fica em {@code ReconstruirCaminhoComFixturesTest} (Docker).
 */
class ReconstruirCaminhoTest {

    private static final ModeloDeFluxo HIDRICO = CatalogoDeEtapasDeTeste.padrao().modelo("procOutorgaAguaSupSub")
            .orElseThrow();
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 8, 0);
    private static final String RAIZ = "r";

    /** Monta o caminho cru da raiz, em ordem. */
    private static final class Caminho {
        private final List<AtividadeBruta> atividades = new ArrayList<>();
        private final List<VariavelBruta> variaveis = new ArrayList<>();
        private int n;

        Caminho atividade(String id, String tipo, int de, Integer ate) {
            atividades.add(new AtividadeBruta(RAIZ, OrigemDaInstancia.RAIZ, RAIZ + "-a" + String.format("%06d", ++n), null,
                    RAIZ, id, null, tipo, T0.plusDays(de), ate == null ? null : T0.plusDays(ate), Optional.empty(), null,
                    Optional.empty()));
            return this;
        }

        Caminho tarefa(String id, int de, Integer ate, MotivoDaTarefa motivo) {
            atividades.add(new AtividadeBruta(RAIZ, OrigemDaInstancia.RAIZ, RAIZ + "-a" + String.format("%06d", ++n), null,
                    RAIZ, id, null, "userTask", T0.plusDays(de), ate == null ? null : T0.plusDays(ate), Optional.empty(),
                    null, Optional.of(new AtividadeBruta.TarefaBruta(id, Optional.empty(), T0.plusDays(de),
                            ate == null ? null : T0.plusDays(ate), motivo))));
            return this;
        }

        Caminho feita(String id, int de, int ate) {
            return tarefa(id, de, ate, MotivoDaTarefa.CONCLUIDA);
        }

        Caminho variavel(String nome, String valor, int quando) {
            variaveis.add(new VariavelBruta(RAIZ, nome, "string", valor, null, T0.plusDays(quando)));
            return this;
        }

        CaminhoReconstruido reconstruir() {
            InstanciaDoHistorico raiz = new InstanciaDoHistorico(RAIZ, OrigemDaInstancia.RAIZ, HIDRICO.chave(), T0, null,
                    null, "ACTIVE", null, false, atividades.size());
            return ReconstruirCaminho.reconstruir(new CaminhoBruto(List.of(raiz), 1, atividades, variaveis, List.of()),
                    HIDRICO, Map.of());
        }
    }

    private static List<String> ids(CaminhoReconstruido c) {
        return c.passagens().stream().map(p -> p.idDaEtapa().orElse("?") + "#" + p.ciclo()).toList();
    }

    @Test
    void caminho_vazio_ou_sem_raiz_nao_tem_passagem() {
        assertThat(ReconstruirCaminho.reconstruir(CaminhoBruto.semInstancia(), HIDRICO, Map.of()).passagens()).isEmpty();
    }

    @Test
    void ciclo_sem_corpo_junta_as_tarefas_seguidas_da_etapa() {
        CaminhoReconstruido c = new Caminho().feita("taskAnalisarRequerimento", 0, 2).feita("taskAnalisarRequerimento", 0, 3)
                .feita("taskRevisarParecerTecnico", 3, 4).feita("taskAnalisarRequerimento", 4, 5).reconstruir();
        assertThat(ids(c)).containsExactly("analise_do_processo#1", "revisao_do_parecer_tecnico#1", "analise_do_processo#2");
        assertThat(c.passagens().getFirst().responsaveis()).hasSize(2);
    }

    @Test
    void duas_tarefas_humanas_seguidas_nao_se_juntam_mas_scripts_sim() {
        CaminhoReconstruido c = new Caminho().feita("taskCadastrarRequerimento", 0, 1)
                .feita("taskCadastrarRequerimento", 1, 2)
                .atividade("taskAtualizarStatusRequerimentoArrecadacao", "serviceTask", 2, 2)
                .atividade("taskEmitirTaxaSefaz", "serviceTask", 2, 2).atividade("taskEmitirTaxaSefaz", "serviceTask", 2, 3)
                .reconstruir();
        assertThat(ids(c)).containsExactly("cadastro_do_requerimento#1", "cadastro_do_requerimento#2",
                "emissao_da_taxa#1");
        assertThat(c.passagens().getLast().repeticoes()).isEqualTo(3);
    }

    @Test
    void tarefa_excluida_nao_e_percorrida_e_tarefa_fora_do_catalogo_vira_etapa_nao_mapeada() {
        CaminhoReconstruido c = new Caminho().tarefa("taskCadastrarRequerimento", 0, 1, MotivoDaTarefa.EXCLUIDA)
                .feita("taskFicticiaForaDoCatalogo", 1, 2).atividade("Gateway_Ficticio", "exclusiveGateway", 2, 2)
                .reconstruir();
        assertThat(c.passagens()).hasSize(1);
        assertThat(c.passagens().getFirst().etapa()).isEmpty();
        assertThat(c.passagens().getFirst().rotulo()).isEqualTo(ResolucaoDeEtapa.ROTULO_NAO_MAPEADA);
        assertThat(c.idsNaoMapeados()).containsExactlyInAnyOrder("taskFicticiaForaDoCatalogo", "Gateway_Ficticio");
    }

    @Test
    void volta_sem_variavel_que_confirme_fica_sem_motivo() {
        CaminhoReconstruido c = new Caminho().feita("taskCadastrarRequerimento", 0, 1)
                .feita("taskConferirDocumentosApresentados", 1, 2).feita("taskCadastrarRequerimento", 2, 3).reconstruir();
        assertThat(c.passagens().get(2).ciclo()).isEqualTo(2);
        assertThat(c.passagens().get(2).motivoDaVolta()).isEmpty();
    }

    @Test
    void duas_decisoes_disputando_a_mesma_volta_deixam_o_motivo_vazio() {
        CaminhoReconstruido c = new Caminho().feita("taskAnalisarRequerimento", 0, 1)
                .feita("taskRevisarParecerTecnico", 1, 2).feita("taskValidarParecerTecnico", 2, 3)
                .variavel("decisaoValidacaoParecerTecnico", "RA", 3).variavel("isRevisaoAnalise", "true", 3)
                .feita("taskAnalisarRequerimento", 3, 4).reconstruir();
        assertThat(c.passagens().getLast().ciclo()).isEqualTo(2);
        assertThat(c.passagens().getLast().motivoDaVolta()).isEmpty();
    }

    @Test
    void variavel_fora_da_janela_nao_decide_a_volta() {
        CaminhoReconstruido c = new Caminho().variavel("decisaoValidacaoParecerTecnico", "RA", 0)
                .feita("taskAnalisarRequerimento", 0, 1).feita("taskRevisarParecerTecnico", 1, 2)
                .feita("taskValidarParecerTecnico", 2, 3).feita("taskAnalisarRequerimento", 3, 4).reconstruir();
        assertThat(c.passagens().getLast().motivoDaVolta()).isEmpty();
    }

    @Test
    void booleano_como_numero_tambem_vale() {
        List<VariavelBruta> vars = List.of(new VariavelBruta(RAIZ, "isRevisaoAnalise", "boolean", null, 1L, T0.plusDays(2)));
        Caminho base = new Caminho().feita("taskAnalisarRequerimento", 0, 1).feita("taskRevisarParecerTecnico", 1, 2)
                .feita("taskAnalisarRequerimento", 2, 3);
        base.variaveis.addAll(vars);
        assertThat(base.reconstruir().passagens().getLast().motivoDaVolta()).contains(MotivoDaVolta.TROCA_DE_ANALISTA);
    }

    @Test
    void rota_vence_o_status_e_a_divergencia_vai_para_o_diagnostico() {
        CaminhoReconstruido c = new Caminho().feita("taskConferirDocumentosApresentados", 0, 1)
                .variavel("statusConferencia", "isento", 1)
                .atividade("taskEmitirTaxaSefaz", "serviceTask", 1, 1).atividade("gtwAguardarPagamento", "eventBasedGateway", 1, 3)
                .reconstruir();
        assertThat(c.taxaPrincipal()).isEqualTo(ClassificacaoDaTaxa.TAXAVEL);
        assertThat(c.diagnosticos()).containsExactly(CaminhoReconstruido.Diagnostico.ROTA_DIVERGE_DO_STATUS);
    }

    @Test
    void isencao_so_sem_passagem_pela_espera() {
        CaminhoReconstruido isento = new Caminho().feita("taskConferirDocumentosApresentados", 0, 1)
                .atividade("taskGerarNumeroProtocolo", "serviceTask", 1, 1).reconstruir();
        assertThat(isento.taxaPrincipal()).isEqualTo(ClassificacaoDaTaxa.ISENTA);
        CaminhoReconstruido taxavel = new Caminho().atividade("gtwAguardarPagamento", "eventBasedGateway", 0, 1)
                .atividade("taskGerarNumeroProtocolo", "serviceTask", 1, 1).reconstruir();
        assertThat(ids(taxavel)).containsExactly("aguardando_pagamento#1");
        assertThat(taxavel.idsNaoMapeados()).contains("taskGerarNumeroProtocolo");
    }

    @Test
    void sem_nome_tira_o_nome_de_todos_os_responsaveis() {
        IdentificadorDoResponsavel id = IdentificadorDoResponsavel.de("00000000005").orElseThrow();
        AtividadeBruta a = new AtividadeBruta(RAIZ, OrigemDaInstancia.RAIZ, "r-a000001", null, RAIZ,
                "taskRevisarParecerTecnico", null, "userTask", T0, T0.plusDays(1), Optional.of(id), null,
                Optional.of(new AtividadeBruta.TarefaBruta("taskRevisarParecerTecnico", Optional.of(id), T0, T0.plusDays(1),
                        MotivoDaTarefa.CONCLUIDA)));
        InstanciaDoHistorico raiz = new InstanciaDoHistorico(RAIZ, OrigemDaInstancia.RAIZ, HIDRICO.chave(), T0, null, null,
                null, null, false, 1);
        CaminhoBruto bruto = new CaminhoBruto(List.of(raiz), 1, List.of(a), List.of(), List.of());
        assertThat(ReconstruirCaminho.paraConsultar(bruto)).containsExactly(id);
        CaminhoReconstruido c = ReconstruirCaminho.reconstruir(bruto, HIDRICO,
                Map.of(id, new IdentificacaoDoResponsavel(Optional.of("Servidor Exemplo 05"), false)));
        assertThat(c.passagens().getFirst().responsaveis().getFirst().nome()).contains("Servidor Exemplo 05");
        assertThat(c.semNomes().passagens().getFirst().responsaveis().getFirst().nome()).isEmpty();
        assertThat(c.toString()).doesNotContain("00000000005");
    }
}
