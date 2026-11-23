package br.gov.mt.sema.rastro.financeiro.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Regra de validade do lançamento (S9, bloco 2), sem banco: cada regra, ignorados por motivo e inconsistências. */
class ClassificarValidadeTest {

    private static final LocalDateTime EMISSAO = LocalDateTime.of(2026, 3, 4, 9, 0);
    private static final String DAR = "FICT-0001";

    private static Lancamento lancamento(long id, String status, String dar, boolean isenta) {
        return new Lancamento(id, 1, status, isenta, "P", dar, EMISSAO.plusMinutes(id), null,
                "PAGA".equals(status) ? EMISSAO.plusDays(3) : null, null, new BigDecimal("980.00"), BigDecimal.ZERO,
                new BigDecimal("28.0000"), null);
    }

    private static LancamentosClassificados classificar(Lancamento... lancamentos) {
        return ClassificarValidade.classificar(new LeituraDosLancamentos(List.of(lancamentos), false));
    }

    @Test
    void cancelada_sem_dar_e_falso_e_ignorada() {
        LancamentosClassificados c = classificar(lancamento(1, "CANCELADA", null, false));
        assertThat(c.mantidos()).isEmpty();
        assertThat(c.ignorados()).extracting(LancamentoIgnorado::motivo).containsExactly(MotivoDoIgnorado.FALSO_SEM_DAR);
        assertThat(c.semLancamento()).isTrue();
    }

    @Test
    void cancelada_com_dar_e_valida() {
        LancamentosClassificados c = classificar(lancamento(1, "CANCELADA", DAR, false));
        assertThat(c.mantidos()).singleElement().satisfies(m -> assertThat(m.consistente()).isTrue());
        assertThat(c.ignorados()).isEmpty();
    }

    @Test
    void dar_em_branco_conta_como_sem_dar() {
        LancamentosClassificados c = classificar(lancamento(1, "CANCELADA", "   ", false));
        assertThat(c.ignorados()).extracting(LancamentoIgnorado::motivo).containsExactly(MotivoDoIgnorado.FALSO_SEM_DAR);
    }

    @Test
    void isenta_sem_dar_e_valida_com_ou_sem_marca_de_isenta() {
        LancamentosClassificados c = classificar(lancamento(1, "ISENTA", null, true), lancamento(2, "ISENTA", null, false));
        assertThat(c.mantidos()).hasSize(2).allSatisfy(m -> assertThat(m.consistente()).isTrue());
    }

    @Test
    void aguardando_emissao_sem_dar_e_valido() {
        assertThat(classificar(lancamento(1, "AGUARDANDO_EMISSAO", null, false)).mantidos())
                .singleElement().satisfies(m -> assertThat(m.consistente()).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DAR})
    void aguardando_emissao_com_taxa_isenta_e_ruido_ignorado(String dar) {
        LancamentosClassificados c = classificar(lancamento(1, "AGUARDANDO_EMISSAO", dar, true));
        assertThat(c.mantidos()).isEmpty();
        assertThat(c.ignorados()).extracting(LancamentoIgnorado::motivo)
                .containsExactly(MotivoDoIgnorado.RUIDO_ISENTO_AGUARDANDO);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDaGuia.class, names = {"AGUARDANDO_PAGAMENTO", "PAGA", "VENCIDA"})
    void com_dar_e_valido(StatusDaGuia status) {
        assertThat(classificar(lancamento(1, status.name(), DAR, false)).mantidos())
                .singleElement().satisfies(m -> assertThat(m.inconsistencias()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = StatusDaGuia.class, names = {"AGUARDANDO_PAGAMENTO", "PAGA", "VENCIDA"})
    void sem_dar_e_mantido_com_inconsistencia_nunca_descartado(StatusDaGuia status) {
        LancamentosClassificados c = classificar(lancamento(1, status.name(), null, false));
        assertThat(c.ignorados()).isEmpty();
        assertThat(c.mantidos()).singleElement()
                .satisfies(m -> assertThat(m.inconsistencias()).contains(Inconsistencia.SEM_DAR));
        assertThat(c.avisos()).extracting(AvisoFinanceiro::codigo)
                .containsExactly(CodigoDoAvisoFinanceiro.LANCAMENTO_INCONSISTENTE);
    }

    @Test
    void paga_sem_data_de_pagamento_e_mantida_com_inconsistencia() {
        Lancamento semData = new Lancamento(1, 1, "PAGA", false, "P", DAR, EMISSAO, null, null, null, null, null, null,
                null);
        assertThat(classificar(semData).mantidos()).singleElement()
                .satisfies(m -> assertThat(m.inconsistencias()).containsExactly(Inconsistencia.PAGA_SEM_DATA_DE_PAGAMENTO));
    }

    @Test
    void paga_sem_dar_e_sem_data_tem_as_duas_inconsistencias() {
        Lancamento l = new Lancamento(1, 1, "PAGA", false, "P", null, EMISSAO, null, null, null, null, null, null, null);
        assertThat(classificar(l).mantidos().getFirst().inconsistencias())
                .containsExactlyInAnyOrder(Inconsistencia.SEM_DAR, Inconsistencia.PAGA_SEM_DATA_DE_PAGAMENTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ESTORNADA", "paga", "PAGO", ""})
    void status_desconhecido_e_mantido_com_aviso(String bruto) {
        LancamentosClassificados c = classificar(lancamento(1, bruto, null, false));
        assertThat(c.ignorados()).isEmpty();
        assertThat(c.mantidos()).singleElement()
                .satisfies(m -> assertThat(m.inconsistencias()).containsExactly(Inconsistencia.STATUS_DESCONHECIDO));
    }

    @Test
    void status_nulo_e_desconhecido_e_status_com_espacos_e_conhecido() {
        Lancamento nulo = new Lancamento(1, 1, null, false, null, null, EMISSAO, null, null, null, null, null, null, null);
        assertThat(classificar(nulo).mantidos().getFirst().inconsistencias())
                .containsExactly(Inconsistencia.STATUS_DESCONHECIDO);
        assertThat(classificar(lancamento(2, " ISENTA ", null, false)).mantidos().getFirst().consistente()).isTrue();
    }

    @Test
    void contagem_de_ignorados_por_motivo_traz_todos_os_motivos() {
        Lancamento[] fin14 = new Lancamento[11];
        for (int i = 0; i < 10; i++) {
            fin14[i] = lancamento(i + 1, "CANCELADA", null, false);
        }
        fin14[10] = lancamento(11, "AGUARDANDO_PAGAMENTO", DAR, false);
        LancamentosClassificados c = classificar(fin14);
        assertThat(c.mantidos()).hasSize(1);
        assertThat(c.ignoradosPorMotivo()).isEqualTo(Map.of(MotivoDoIgnorado.FALSO_SEM_DAR, 10,
                MotivoDoIgnorado.RUIDO_ISENTO_AGUARDANDO, 0));
        assertThat(c.avisos()).isEmpty();
    }

    @Test
    void ordem_por_emissao_sem_data_no_fim_e_id_no_empate() {
        Lancamento semData = new Lancamento(1, 1, "AGUARDANDO_EMISSAO", false, "P", null, null, null, null, null, null,
                null, null, null);
        Lancamento tarde = new Lancamento(2, 1, "PAGA", false, "P", DAR, EMISSAO.plusDays(9), null, EMISSAO.plusDays(10),
                null, null, null, null, null);
        Lancamento cedoB = new Lancamento(4, 1, "PAGA", false, "P", DAR, EMISSAO, null, EMISSAO, null, null, null, null,
                null);
        Lancamento cedoA = new Lancamento(3, 1, "PAGA", false, "P", DAR, EMISSAO, null, EMISSAO, null, null, null, null,
                null);
        assertThat(classificar(semData, tarde, cedoB, cedoA).mantidos())
                .extracting(m -> m.lancamento().id()).containsExactly(3L, 4L, 2L, 1L);
    }

    @Test
    void encerrado_valido_continua_na_lista() {
        Lancamento encerrado = new Lancamento(1, 1, "AGUARDANDO_PAGAMENTO", false, "P", DAR, EMISSAO, null, null,
                EMISSAO.plusDays(2), null, null, null, null);
        assertThat(classificar(encerrado).mantidos()).singleElement()
                .satisfies(m -> assertThat(m.lancamento().ativo()).isFalse());
    }

    @Test
    void alem_do_limite_vira_aviso() {
        LancamentosClassificados c = ClassificarValidade.classificar(
                new LeituraDosLancamentos(List.of(lancamento(1, "PAGA", DAR, false)), true));
        assertThat(c.avisos()).extracting(AvisoFinanceiro::codigo)
                .containsExactly(CodigoDoAvisoFinanceiro.LANCAMENTOS_ALEM_DO_LIMITE);
    }

    @Test
    void sem_lancamento_nao_tem_aviso() {
        LancamentosClassificados c = ClassificarValidade.classificar(LeituraDosLancamentos.NENHUM);
        assertThat(c.semLancamento()).isTrue();
        assertThat(c.avisos()).isEmpty();
        assertThat(c.ignoradosPorMotivo().values()).containsOnly(0);
    }

    @Test
    void aviso_nao_traz_id_dar_nem_valor() {
        LancamentosClassificados c = classificar(lancamento(987654, "PAGA", null, false),
                lancamento(987655, "ESTORNADA", DAR, false));
        assertThat(c.avisos()).singleElement().satisfies(a -> {
            assertThat(a.texto()).startsWith("2 lançamentos").doesNotContain("987654").doesNotContain("FICT")
                    .doesNotContain("980");
            assertThat(a.gravidade()).isEqualTo(AvisoFinanceiro.Gravidade.ATENCAO);
        });
    }
}
