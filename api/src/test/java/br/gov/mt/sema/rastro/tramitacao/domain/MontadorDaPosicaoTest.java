package br.gov.mt.sema.rastro.tramitacao.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo.Execucao;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo.Filha;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo.InstanciaRaiz;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo.Percorrida;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo.Tarefa;
import org.junit.jupiter.api.Test;

/**
 * Regras do montador sem banco (S7, bloco 3), com um modelo pequeno e ids técnicos fictícios no formato do BPMN. Os
 * cenários completos (H01 a L07) rodam contra o Oracle de teste em {@code CamundaPosicaoJdbcTest}.
 */
class MontadorDaPosicaoTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 9, 0);
    private static final String PI = "pi-1";

    private static Etapa etapa(String id, int ordem, Ramo ramo, TipoDeEtapa tipo, boolean multi, String idTecnico,
            List<String> filhos, List<String> semPassagemPor) {
        return new Etapa(id, "Rótulo " + id, ordem, tipo, PapelDoResponsavel.GRUPO, ramo, multi, List.of(idTecnico),
                filhos, semPassagemPor);
    }

    private static final ModeloDeFluxo MODELO = ModeloDeFluxo.criar("teste", "procTeste", "Modelo de teste", List.of("T"),
            List.of(
                    etapa("conferencia", 2, Ramo.PRINCIPAL, TipoDeEtapa.TAREFA_HUMANA, false, "taskConferir", List.of(), List.of()),
                    etapa("pagamento", 4, Ramo.PRINCIPAL, TipoDeEtapa.ESPERA, false, "gtwPagar", List.of(), List.of()),
                    etapa("isencao", 5, Ramo.PRINCIPAL, TipoDeEtapa.AUTOMATICA, false, "taskProtocolo", List.of(),
                            List.of("gtwPagar")),
                    etapa("analise", 8, Ramo.PRINCIPAL, TipoDeEtapa.TAREFA_HUMANA, true, "taskAnalisar", List.of(), List.of()),
                    etapa("emissao", 11, Ramo.PRINCIPAL, TipoDeEtapa.EMISSAO, false, "taskEmitir", List.of("procFilhaA",
                            "procFilhaB"), List.of()),
                    etapa("correcao", 1, Ramo.CORRECAO, TipoDeEtapa.TAREFA_HUMANA, false, "taskCorrigir", List.of(),
                            List.of())),
            List.of(new FinalDoFluxo("concluido", "Concluído", "evtFim")),
            List.of(new IdIgnorado("Activity_suporte", "Suporte", IdIgnorado.Motivo.SUPORTE),
                    new IdIgnorado("Gateway_ruido", "Ruído", IdIgnorado.Motivo.RUIDO)));

    private static InstanciaRaiz ativa(String id, LocalDateTime inicio) {
        return new InstanciaRaiz(id, inicio, null, null, "ACTIVE", false);
    }

    private static Tarefa tarefa(String idTecnico, LocalDateTime criada) {
        return new Tarefa(PI, idTecnico, criada, Optional.empty(), List.of());
    }

    private static Percorrida percorrida(String idTecnico, LocalDateTime fim, boolean tarefa) {
        return new Percorrida(idTecnico, fim.minusHours(1), fim, tarefa, Optional.empty());
    }

    private static PosicaoAtual montar(List<Execucao> execucoes, List<Tarefa> tarefas, List<Filha> filhas,
            List<Percorrida> percorridas) {
        return MontadorDaPosicao.montar(MODELO, new LeituraDoFluxo(List.of(ativa(PI, T0)), execucoes, tarefas, filhas,
                List.of(), percorridas));
    }

    @Test
    void sem_instancia() {
        PosicaoAtual p = MontadorDaPosicao.montar(MODELO, LeituraDoFluxo.semInstancia());
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.SEM_INSTANCIA);
        assertThat(p.marcas()).containsExactly(MarcaDaPosicao.SEM_INSTANCIA);
        assertThat(p.rotuloDoModelo()).contains("Modelo de teste");
    }

    @Test
    void encerrada_com_final_mapeado_e_com_final_desconhecido() {
        LocalDateTime fim = T0.plusDays(3);
        PosicaoAtual p = MontadorDaPosicao.montar(MODELO, LeituraDoFluxo.encerrada(List.of(
                new InstanciaRaiz(PI, T0, fim, "evtFim", "COMPLETED", false))));
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.ENCERRADO);
        assertThat(p.fim()).map(FinalDoFluxo::rotulo).contains("Concluído");
        assertThat(p.encerradaEm()).contains(fim);
        assertThat(p.principal()).isEmpty();
        assertThat(p.diagnostico()).contains("STATE_=COMPLETED");

        PosicaoAtual desconhecido = MontadorDaPosicao.montar(MODELO, LeituraDoFluxo.encerrada(List.of(
                new InstanciaRaiz(PI, T0, fim, "Event_novo", "INTERNALLY_TERMINATED", true))));
        assertThat(desconhecido.estado()).isEqualTo(EstadoDaPosicao.ENCERRADO);
        assertThat(desconhecido.fim()).isEmpty();
        assertThat(desconhecido.marcas()).contains(MarcaDaPosicao.ETAPA_NAO_MAPEADA);
        assertThat(desconhecido.idsNaoMapeados()).containsExactly("Event_novo");
        assertThat(desconhecido.diagnostico()).hasValueSatisfying(d -> assertThat(d).contains("com motivo de exclusão"));
    }

    @Test
    void varias_instancias_vale_a_primeira_da_leitura_com_marca() {
        PosicaoAtual p = MontadorDaPosicao.montar(MODELO, new LeituraDoFluxo(
                List.of(ativa(PI, T0), new InstanciaRaiz("pi-antiga", T0.minusYears(1), T0.minusMonths(6), "evtFim",
                        "COMPLETED", false)),
                List.of(), List.of(tarefa("taskConferir", T0)), List.of(), List.of(), List.of()));
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.ATIVO);
        assertThat(p.marcas()).contains(MarcaDaPosicao.VARIAS_INSTANCIAS);
        assertThat(p.principal()).map(ElementoDaPosicao::rotulo).contains("Rótulo conferencia");
    }

    @Test
    void espera_so_vale_com_assinatura_de_evento() {
        PosicaoAtual confirmada = montar(List.of(new Execucao("gtwPagar", T0, T0.plusMinutes(1))), List.of(), List.of(),
                List.of());
        assertThat(confirmada.principal()).hasValueSatisfying(e -> {
            assertThat(e.etapa()).map(Etapa::id).contains("pagamento");
            assertThat(e.desde()).isEqualTo(T0.plusMinutes(1));
        });

        PosicaoAtual semAssinatura = montar(List.of(new Execucao("gtwPagar", T0, null)), List.of(), List.of(), List.of());
        assertThat(semAssinatura.principal()).hasValueSatisfying(e -> assertThat(e.mapeado()).isFalse());
        assertThat(semAssinatura.marcas()).contains(MarcaDaPosicao.ETAPA_NAO_MAPEADA);
    }

    @Test
    void isencao_depende_do_percurso() {
        PosicaoAtual isento = montar(List.of(new Execucao("taskProtocolo", T0, null)), List.of(), List.of(),
                List.of(percorrida("taskConferir", T0.minusDays(1), true)));
        assertThat(isento.principal()).flatMap(ElementoDaPosicao::etapa).map(Etapa::id).contains("isencao");

        PosicaoAtual pago = montar(List.of(new Execucao("taskProtocolo", T0, null)), List.of(), List.of(),
                List.of(percorrida("gtwPagar", T0.minusDays(1), false)));
        assertThat(pago.principal()).hasValueSatisfying(e -> assertThat(e.mapeado()).isFalse());
        assertThat(pago.idsNaoMapeados()).containsExactly("taskProtocolo");
    }

    @Test
    void multi_instancia_conta_o_ciclo_atual_e_ignora_ciclos_anteriores() {
        LocalDateTime ciclo = T0.plusDays(5);
        PosicaoAtual p = montar(
                List.of(new Execucao("taskAnalisar#multiInstanceBody", ciclo, null), new Execucao("taskAnalisar", ciclo, null)),
                List.of(tarefa("taskAnalisar", ciclo)),
                List.of(),
                List.of(new Percorrida("taskAnalisar", ciclo, ciclo.plusHours(2), true, Optional.empty()),
                        new Percorrida("taskAnalisar", ciclo, ciclo.plusHours(3), true, Optional.empty()),
                        // Ciclo anterior (antes da devolução): não conta.
                        new Percorrida("taskAnalisar", T0, T0.plusHours(1), true, Optional.empty())));
        ElementoDaPosicao analise = p.principal().orElseThrow();
        assertThat(analise.multiInstancia()).hasValueSatisfying(m -> {
            assertThat(m.concluidos()).contains(2);
            assertThat(m.ativas()).isEqualTo(1);
            assertThat(m.total()).contains(3);
        });
        assertThat(analise.desde()).isEqualTo(ciclo);
        assertThat(analise.desdeUltimaEntrada()).isTrue();
        assertThat(p.emParalelo()).isEmpty();
    }

    @Test
    void multi_instancia_sem_ciclo_conhecido_nao_inventa_o_total() {
        PosicaoAtual p = montar(List.of(), List.of(tarefa("taskAnalisar", T0), tarefa("taskAnalisar", T0)), List.of(),
                List.of(percorrida("taskAnalisar", T0.minusDays(1), true)));
        assertThat(p.principal()).flatMap(ElementoDaPosicao::multiInstancia).hasValueSatisfying(m -> {
            assertThat(m.ativas()).isEqualTo(2);
            assertThat(m.concluidos()).isEmpty();
            assertThat(m.total()).isEmpty();
        });
    }

    @Test
    void filhas_de_emissao_sao_pontos_separados_e_a_chamada_nao_duplica() {
        PosicaoAtual p = montar(List.of(new Execucao("taskEmitir", T0, null), new Execucao("taskEmitir", T0, null)),
                List.of(new Tarefa("filha-a", "taskDaFilha", T0, Optional.empty(), List.of("grpEmissor"))),
                List.of(new Filha("filha-a", "procFilhaA", T0), new Filha("filha-b", "procFilhaB", T0.plusMinutes(5))),
                List.of());
        assertThat(p.principal()).hasValueSatisfying(e -> {
            assertThat(e.etapa()).map(Etapa::id).contains("emissao");
            assertThat(e.processoFilho()).contains("procFilhaA");
            assertThat(e.tarefas()).hasSize(1);
        });
        assertThat(p.emParalelo()).singleElement().satisfies(e -> assertThat(e.processoFilho()).contains("procFilhaB"));
    }

    @Test
    void principal_e_a_menor_ordem_do_ramo_principal_e_correcao_vem_depois() {
        PosicaoAtual p = montar(List.of(new Execucao("gtwPagar", T0, T0)),
                List.of(tarefa("taskCorrigir", T0.minusDays(2)), tarefa("taskConferir", T0.plusDays(1))), List.of(),
                List.of());
        assertThat(p.principal()).flatMap(ElementoDaPosicao::etapa).map(Etapa::id).contains("conferencia");
        assertThat(p.emParalelo()).extracting(e -> e.etapa().orElseThrow().id()).containsExactly("pagamento", "correcao");

        PosicaoAtual soCorrecao = montar(List.of(), List.of(tarefa("taskCorrigir", T0)), List.of(), List.of());
        assertThat(soCorrecao.principal()).flatMap(ElementoDaPosicao::etapa).map(Etapa::id).contains("correcao");
    }

    @Test
    void suporte_sozinho_mostra_a_ultima_etapa_real_sem_desde() {
        PosicaoAtual p = montar(List.of(new Execucao("Activity_suporte", T0, null)),
                List.of(tarefa("Activity_suporte", T0)), List.of(),
                List.of(percorrida("Gateway_ruido", T0.minusHours(1), false),
                        percorrida("taskConferir", T0.minusHours(2), true),
                        percorrida("taskInexistente", T0.minusHours(3), false)));
        assertThat(p.marcas()).contains(MarcaDaPosicao.EM_SUPORTE);
        assertThat(p.emSuporteDesde()).contains(T0);
        assertThat(p.principal()).hasValueSatisfying(e -> {
            assertThat(e.etapa()).map(Etapa::id).contains("conferencia");
            assertThat(e.desde()).isNull();
        });
    }

    @Test
    void suporte_ao_lado_de_etapa_real_so_marca() {
        PosicaoAtual p = montar(List.of(), List.of(tarefa("Activity_suporte", T0), tarefa("taskConferir", T0)), List.of(),
                List.of());
        assertThat(p.marcas()).contains(MarcaDaPosicao.EM_SUPORTE);
        assertThat(p.principal()).flatMap(ElementoDaPosicao::etapa).map(Etapa::id).contains("conferencia");
        assertThat(p.emParalelo()).isEmpty();
    }

    @Test
    void id_desconhecido_vira_ponto_nao_mapeado_e_ignorado_de_ruido_some() {
        PosicaoAtual p = montar(List.of(new Execucao("Gateway_ruido", T0, null)), List.of(tarefa("taskNova", T0)),
                List.of(), List.of());
        assertThat(p.principal()).hasValueSatisfying(e -> {
            assertThat(e.rotulo()).isEqualTo("Etapa não mapeada");
            assertThat(e.idTecnico()).isEqualTo("taskNova");
        });
        assertThat(p.emParalelo()).isEmpty();
        assertThat(p.idsNaoMapeados()).containsExactly("taskNova");
    }

    @Test
    void ativa_sem_nenhum_ponto_diz_que_nao_sabe() {
        PosicaoAtual p = montar(List.of(), List.of(), List.of(), List.of());
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.ATIVO);
        assertThat(p.principal()).isEmpty();
        assertThat(p.marcas()).contains(MarcaDaPosicao.ETAPA_NAO_MAPEADA);
    }

    @Test
    void complementacao_fica_a_parte() {
        PosicaoAtual p = MontadorDaPosicao.montar(MODELO, new LeituraDoFluxo(List.of(ativa(PI, T0)), List.of(),
                List.of(tarefa("taskConferir", T0)), List.of(),
                List.of(new LeituraDoFluxo.Complementacao("c2", T0.plusDays(2)),
                        new LeituraDoFluxo.Complementacao("c1", T0.plusDays(1))),
                List.of()));
        assertThat(p.complementacao()).hasValueSatisfying(c -> {
            assertThat(c.quantidade()).isEqualTo(2);
            assertThat(c.desde()).isEqualTo(T0.plusDays(1));
        });
        assertThat(p.emParalelo()).isEmpty();
    }

    @Test
    void identificador_do_responsavel_nao_aparece_em_texto() {
        Tarefa t = new Tarefa(PI, "taskConferir", T0, IdentificadorDoResponsavel.de("00000000001"), List.of());
        PosicaoAtual p = montar(List.of(), List.of(t), List.of(), List.of());
        assertThat(p.toString()).doesNotContain("00000000001");
        assertThat(IdentificadorDoResponsavel.de("  ")).isEmpty();
    }
}
