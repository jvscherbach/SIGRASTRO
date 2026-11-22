package br.gov.mt.sema.rastro.tramitacao.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.EtapaAproximada;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.Marco;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.TipoDeMarco;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.MapaDeEventosDeTeste;
import org.junit.jupiter.api.Test;

/** Linha do tempo aproximada do plano B (S8, bloco 5), sem banco, com o mapa real {@code eventos-historico.yaml}. */
class MontarHistoricoAproximadoTest {

    private static final ModeloDeFluxo HIDRICO = CatalogoDeEtapasDeTeste.padrao().modelo("procOutorgaAguaSupSub")
            .orElseThrow();
    private static final ModeloDeFluxo LAC = CatalogoDeEtapasDeTeste.padrao().modelo("procLicenciamentoLac").orElseThrow();
    private static final LocalDateTime T0 = LocalDateTime.of(2021, 5, 10, 9, 0);

    private static LeituraDoPlanoB.Evento ev(String evento, int dia) {
        return new LeituraDoPlanoB.Evento(evento, T0.plusDays(dia));
    }

    private static HistoricoAproximado montar(ModeloDeFluxo modelo, LeituraDoPlanoB.Datas datas,
            LeituraDoPlanoB.Evento... eventos) {
        return MontarHistoricoAproximado.montar(new LeituraDoPlanoB(Optional.of("CONCLUIDO"), datas,
                Arrays.asList(eventos), false), modelo, MapaDeEventosDeTeste.padrao());
    }

    private static List<String> ids(HistoricoAproximado h) {
        return h.etapas().stream().map(e -> e.etapa().id()).toList();
    }

    @Test
    void eventos_viram_etapas_do_catalogo_em_ordem_de_data_sem_ciclos() {
        HistoricoAproximado h = montar(HIDRICO, new LeituraDoPlanoB.Datas(T0, T0.plusDays(12), null, null, null),
                ev("CRIACAO_REQUERIMENTO", 0), ev("AGUARDANDO_CONFERENCIA", 1), ev("CHECKLIST_APROVADO", 3),
                ev("AGUARDANDO_PAGAMENTO_TAXA", 4), ev("PAGAMENTO_CONFIRMADO", 10), ev("PROTOCOLO", 12),
                ev("EM_ANALISE", 14), ev("REVISAR_PARECER_TECNICO", 40), ev("VALIDAR_PARECER_TECNICO", 60),
                ev("TITULOS_EMITIDOS", 90), ev("TITULACOES_DISPONIVEIS", 100), ev("CONCLUSAO_REQUERIMENTO", 120));
        assertThat(ids(h)).containsExactly("cadastro_do_requerimento", "conferencia_dos_documentos",
                "aguardando_pagamento", "analise_do_processo", "emissao_do_ato", "registro_do_comprovante_de_publicacao");
        // Revisão e validação não aparecem separadas: caem na análise (3 eventos).
        assertThat(h.etapas().get(3).eventos()).isEqualTo(3);
        // Saída = entrada da seguinte; a última fecha na conclusão.
        assertThat(h.etapas().get(0).saida()).contains(T0.plusDays(1));
        assertThat(h.etapas().getLast().saida()).contains(T0.plusDays(120));
        assertThat(h.marcos()).extracting(Marco::tipo).containsExactly(TipoDeMarco.ABERTURA, TipoDeMarco.PROTOCOLO,
                TipoDeMarco.CONCLUSAO);
        assertThat(h.aproximado()).isTrue();
        assertThat(h.semDetalhamento()).isFalse();
        assertThat(h.eventosIgnorados()).isZero();
    }

    @Test
    void eventos_fora_de_ordem_sao_ordenados_pela_data() {
        HistoricoAproximado h = montar(HIDRICO, LeituraDoPlanoB.Datas.NENHUMA, ev("AGUARDANDO_CONFERENCIA", 2),
                ev("CRIACAO_REQUERIMENTO", 0));
        assertThat(ids(h)).containsExactly("cadastro_do_requerimento", "conferencia_dos_documentos");
    }

    @Test
    void evento_desconhecido_e_ignorado_e_contado() {
        HistoricoAproximado h = montar(HIDRICO, new LeituraDoPlanoB.Datas(T0, null, null, T0.plusDays(40), null),
                ev("CRIACAO_REQUERIMENTO", 0), ev("AGUARDANDO_CONFERENCIA", 1), ev("EVENTO_FICTICIO_DESCONHECIDO", 2),
                ev("CHECKLIST_REPROVADO", 5));
        assertThat(ids(h)).containsExactly("cadastro_do_requerimento", "conferencia_dos_documentos");
        assertThat(h.eventosIgnorados()).isEqualTo(1);
        // Expirado: a última etapa fecha na expiração do requerimento.
        assertThat(h.etapas().getLast().saida()).contains(T0.plusDays(40));
    }

    @Test
    void evento_que_o_modelo_nao_tem_e_ignorado_no_lac() {
        HistoricoAproximado h = montar(LAC, LeituraDoPlanoB.Datas.NENHUMA, ev("CRIACAO_REQUERIMENTO", 0),
                ev("EM_ANALISE", 2), ev("TITULOS_EMITIDOS", 5));
        assertThat(ids(h)).containsExactly("cadastro_do_requerimento", "conclusao");
        assertThat(h.eventosIgnorados()).isEqualTo(1);
    }

    @Test
    void mesma_etapa_mais_adiante_vira_outra_linha_sem_numero_de_ciclo() {
        HistoricoAproximado h = montar(HIDRICO, LeituraDoPlanoB.Datas.NENHUMA, ev("AGUARDANDO_CONFERENCIA", 0),
                ev("CRIACAO_REQUERIMENTO", 1), ev("AGUARDANDO_CONFERENCIA", 2));
        assertThat(ids(h)).containsExactly("conferencia_dos_documentos", "cadastro_do_requerimento",
                "conferencia_dos_documentos");
        assertThat(h.etapas().getLast().saida()).isEmpty();
    }

    @Test
    void sem_eventos_so_situacao_e_datas() {
        HistoricoAproximado h = montar(HIDRICO, new LeituraDoPlanoB.Datas(T0, null, T0.plusDays(3), null, null));
        assertThat(h.semDetalhamento()).isTrue();
        assertThat(h.situacao()).contains("CONCLUIDO");
        assertThat(h.marcos()).extracting(Marco::tipo).containsExactly(TipoDeMarco.ABERTURA, TipoDeMarco.CANCELAMENTO);
        assertThat(HistoricoAproximado.SEM_DETALHAMENTO).isEqualTo("Sem detalhamento disponível");
    }

    @Test
    void data_do_requerimento_vence_o_evento_de_marco() {
        HistoricoAproximado h = montar(HIDRICO, new LeituraDoPlanoB.Datas(null, T0.plusDays(12), null, null, null),
                ev("PROTOCOLO", 11));
        assertThat(h.marcos()).containsExactly(new Marco(TipoDeMarco.PROTOCOLO, T0.plusDays(12)));
    }

    @Test
    void nao_ha_responsavel_no_plano_b() {
        assertThat(Arrays.stream(HistoricoAproximado.class.getRecordComponents()).map(c -> c.getName()))
                .noneMatch(n -> n.toLowerCase().contains("respons"));
        assertThat(Arrays.stream(EtapaAproximada.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("etapa", "entrada", "saida", "eventos");
    }

    @Test
    void selo_e_o_texto_fixo_do_prompt_e_o_do_aviso() {
        assertThat(HistoricoAproximado.SELO)
                .isEqualTo("Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo");
        assertThat(br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDoConfrontoDeTeste.padrao()
                .aviso(CodigoDoAviso.HISTORICO_APROXIMADO, "").texto()).isEqualTo(HistoricoAproximado.SELO);
    }
}
