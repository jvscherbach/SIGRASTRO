package br.gov.mt.sema.rastro.financeiro.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Marcas financeiras nas passagens e etiqueta "da correção" (S9, bloco 5), sem banco. Datas como em
 * {@code CenariosFicticiosDaS9}: hoje = 2026-03-20; dia n = 2026-03-02 + n.
 */
class MarcarPassagensTest {

    private static final ZoneId CUIABA = ZoneId.of("America/Cuiaba");
    private static final LocalDate HOJE = LocalDate.of(2026, 3, 20);
    private static final Clock RELOGIO = Clock.fixed(HOJE.atTime(10, 0).atZone(CUIABA).toInstant(), CUIABA);

    private static LocalDateTime dia(int n) {
        return LocalDateTime.of(2026, 3, 2, 9, 0).plusDays(n);
    }

    private static PassagemDoFluxo p(String etapa, boolean correcao, LocalDateTime entrada) {
        return new PassagemDoFluxo(Optional.of(etapa), correcao, entrada);
    }

    private static Lancamento guia(long id, String status, String dar, LocalDateTime emissao, LocalDate vencimento,
            LocalDateTime pagamento) {
        return new Lancamento(id, 1, status, false, "P", dar, emissao, vencimento == null ? null : vencimento.atStartOfDay(),
                pagamento, null, new BigDecimal("980.00"), BigDecimal.ZERO, null, null);
    }

    private static HistoricoFinanceiro historico(CaminhoDoFluxo caminho, Lancamento... lancamentos) {
        return AvaliarSituacao.avaliar(
                ClassificarValidade.classificar(new LeituraDosLancamentos(List.of(lancamentos), false)), RELOGIO, caminho);
    }

    private static List<MarcaFinanceira.Tipo> tipos(List<MarcaFinanceira> marcas) {
        return marcas.stream().map(MarcaFinanceira::tipo).toList();
    }

    /** FIN11: taxa inicial paga e taxa da correção aguardando (emitida no dia 14, correção a partir do dia 12). */
    private static final CaminhoDoFluxo FIN11 = new CaminhoDoFluxo(List.of(
            p("cadastro_do_requerimento", false, dia(0)),
            p("emissao_da_taxa", false, dia(2)),
            p("aguardando_pagamento", false, dia(2).plusHours(1)),
            p("analise_do_processo", false, dia(8)),
            p("correcao_do_requerimento", true, dia(12)),
            p("emissao_da_taxa_correcao", true, dia(14)),
            p("aguardando_pagamento_correcao", true, dia(14).plusHours(1))));

    private static Lancamento[] lancamentosDoFin11() {
        return new Lancamento[] {
                guia(1, "PAGA", "FICT-0011", dia(2), dia(12).toLocalDate(), dia(5)),
                guia(2, "AGUARDANDO_PAGAMENTO", "FICT-0111", dia(14), HOJE.plusDays(10), null)};
    }

    @Test
    void fin11_marcas_nas_passagens_certas() {
        HistoricoFinanceiro h = historico(FIN11, lancamentosDoFin11());
        List<List<MarcaFinanceira>> marcas = MarcarPassagens.marcar(h, FIN11);
        assertThat(marcas).hasSize(FIN11.passagens().size());
        assertThat(marcas.get(0)).isEmpty();
        assertThat(tipos(marcas.get(1))).containsExactly(MarcaFinanceira.Tipo.EMITIDA_EM, MarcaFinanceira.Tipo.NUMERO_DAR);
        assertThat(marcas.get(1).get(1).valor()).contains("FICT-0011");
        assertThat(tipos(marcas.get(2))).containsExactly(MarcaFinanceira.Tipo.VENCE_EM, MarcaFinanceira.Tipo.PAGA_EM);
        assertThat(marcas.get(2).get(1).data()).contains(dia(5).toLocalDate());
        assertThat(marcas.get(3)).isEmpty();
        assertThat(marcas.get(4)).isEmpty();
        assertThat(tipos(marcas.get(5))).containsExactly(MarcaFinanceira.Tipo.EMITIDA_EM, MarcaFinanceira.Tipo.NUMERO_DAR,
                MarcaFinanceira.Tipo.DA_CORRECAO);
        assertThat(tipos(marcas.get(6))).containsExactly(MarcaFinanceira.Tipo.VENCE_EM, MarcaFinanceira.Tipo.DA_CORRECAO);
        assertThat(marcas.get(6).getFirst().data()).contains(HOJE.plusDays(10));
    }

    @Test
    void fin11_rotulos_primeira_taxa_e_taxa_da_correcao() {
        HistoricoFinanceiro h = historico(FIN11, lancamentosDoFin11());
        assertThat(h.lancamentos()).extracting(a -> a.rotulo().orElseThrow())
                .containsExactly("1ª taxa", LancamentoAvaliado.ROTULO_DA_CORRECAO);
    }

    @Test
    void sem_caminho_nao_ha_da_correcao_nem_marca() {
        HistoricoFinanceiro h = historico(CaminhoDoFluxo.NENHUM, lancamentosDoFin11());
        assertThat(h.lancamentos()).extracting(a -> a.rotulo().orElseThrow()).containsExactly("1ª taxa", "2ª taxa");
        assertThat(MarcarPassagens.marcar(h, CaminhoDoFluxo.NENHUM)).isEmpty();
    }

    @Test
    void emitida_antes_da_entrada_na_correcao_nao_e_da_correcao() {
        CaminhoDoFluxo caminho = new CaminhoDoFluxo(List.of(p("correcao_do_requerimento", true, dia(12))));
        HistoricoFinanceiro h = historico(caminho,
                guia(1, "AGUARDANDO_PAGAMENTO", "FICT-0002", dia(12).minusHours(1), HOJE.plusDays(3), null));
        assertThat(h.lancamentos().getFirst().daCorrecao()).isFalse();
    }

    @Test
    void fin03_taxa_vencida_marcada_na_espera() {
        CaminhoDoFluxo caminho = new CaminhoDoFluxo(List.of(
                p("emissao_da_taxa", false, dia(2)), p("aguardando_pagamento", false, dia(2).plusHours(1))));
        HistoricoFinanceiro h = historico(caminho,
                guia(1, "AGUARDANDO_PAGAMENTO", "FICT-0003", dia(2), HOJE.minusDays(1), null));
        List<List<MarcaFinanceira>> marcas = MarcarPassagens.marcar(h, caminho);
        assertThat(tipos(marcas.get(1))).containsExactly(MarcaFinanceira.Tipo.VENCE_EM, MarcaFinanceira.Tipo.TAXA_VENCIDA);
        assertThat(marcas.get(1).get(1).data()).contains(HOJE.minusDays(1));
    }

    @Test
    void fin05_isento_de_taxa_na_isencao_confirmada() {
        CaminhoDoFluxo caminho = new CaminhoDoFluxo(List.of(
                p("conferencia_dos_documentos", false, dia(1)), p("isencao_confirmada", false, dia(2))));
        Lancamento isenta = new Lancamento(1, 1, "ISENTA", true, "P", null, dia(2), null, null, null, null, null, null,
                null);
        List<List<MarcaFinanceira>> marcas = MarcarPassagens.marcar(historico(caminho, isenta), caminho);
        assertThat(marcas.get(0)).isEmpty();
        assertThat(marcas.get(1)).singleElement().satisfies(m -> {
            assertThat(m.tipo()).isEqualTo(MarcaFinanceira.Tipo.ISENTO_DE_TAXA);
            assertThat(m.tipo().rotulo()).isEqualTo("Isento de Taxa");
            assertThat(m.data()).contains(dia(2).toLocalDate());
            assertThat(m.dataAConfirmar()).isFalse();
        });
    }

    @Test
    void isencoes_e_passagens_em_numero_diferente_nao_marcam() {
        CaminhoDoFluxo caminho = new CaminhoDoFluxo(List.of(
                p("isencao_confirmada", false, dia(2)), p("isencao_confirmada", false, dia(9))));
        Lancamento isenta = new Lancamento(1, 1, "ISENTA", true, "P", null, dia(2), null, null, null, null, null, null,
                null);
        assertThat(MarcarPassagens.marcar(historico(caminho, isenta), caminho)).allSatisfy(m -> assertThat(m).isEmpty());
    }

    @Test
    void guia_emitida_antes_de_qualquer_emissao_nao_e_casada() {
        CaminhoDoFluxo caminho = new CaminhoDoFluxo(List.of(
                p("emissao_da_taxa", false, dia(5)), p("aguardando_pagamento", false, dia(5).plusHours(1))));
        HistoricoFinanceiro h = historico(caminho,
                guia(1, "AGUARDANDO_PAGAMENTO", "FICT-0002", dia(2), HOJE.plusDays(3), null));
        assertThat(MarcarPassagens.marcar(h, caminho)).allSatisfy(m -> assertThat(m).isEmpty());
    }

    @Test
    void emissao_sem_hora_casa_pela_data() {
        CaminhoDoFluxo caminho = new CaminhoDoFluxo(List.of(
                p("emissao_da_taxa", false, dia(2)), p("aguardando_pagamento", false, dia(2).plusHours(1))));
        HistoricoFinanceiro h = historico(caminho, guia(1, "AGUARDANDO_PAGAMENTO", "FICT-0002",
                dia(2).toLocalDate().atStartOfDay(), HOJE.plusDays(3), null));
        assertThat(tipos(MarcarPassagens.marcar(h, caminho).getFirst()))
                .containsExactly(MarcaFinanceira.Tipo.EMITIDA_EM, MarcaFinanceira.Tipo.NUMERO_DAR);
    }

    @Test
    void cancelada_e_reemitida_a_espera_mostra_a_mais_recente() {
        CaminhoDoFluxo caminho = new CaminhoDoFluxo(List.of(
                p("emissao_da_taxa", false, dia(2)), p("aguardando_pagamento", false, dia(2).plusHours(1))));
        Lancamento cancelada = new Lancamento(1, 1, "CANCELADA", false, "P", "FICT-0008", dia(2), HOJE.minusDays(5)
                .atStartOfDay(), null, dia(3), null, null, null, null);
        Lancamento nova = guia(2, "AGUARDANDO_PAGAMENTO", "FICT-0009", dia(3), HOJE.plusDays(4), null);
        List<List<MarcaFinanceira>> marcas = MarcarPassagens.marcar(historico(caminho, cancelada, nova), caminho);
        assertThat(marcas.get(0)).filteredOn(m -> m.tipo() == MarcaFinanceira.Tipo.NUMERO_DAR)
                .extracting(m -> m.valor().orElseThrow()).containsExactly("FICT-0008", "FICT-0009");
        assertThat(marcas.get(1)).singleElement().satisfies(m -> assertThat(m.data()).contains(HOJE.plusDays(4)));
    }
}
