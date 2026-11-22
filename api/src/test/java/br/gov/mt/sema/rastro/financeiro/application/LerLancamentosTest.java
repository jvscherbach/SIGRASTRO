package br.gov.mt.sema.rastro.financeiro.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.financeiro.application.port.CaminhoDaTramitacao;
import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.domain.CaminhoDoFluxo;
import br.gov.mt.sema.rastro.financeiro.domain.Lancamento;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import br.gov.mt.sema.rastro.financeiro.domain.MotivoDoIgnorado;
import br.gov.mt.sema.rastro.financeiro.domain.ResumoFinanceiro;
import org.junit.jupiter.api.Test;

/** Caso de uso da leitura (S9, bloco 2): aplica a regra e devolve todo id pedido no lote, numa chamada à porta. */
class LerLancamentosTest {

    private static final Lancamento FALSO = new Lancamento(1, 10, "CANCELADA", false, "P", null, null, null, null, null,
            null, null, null, null);
    private static final Lancamento PAGO = new Lancamento(2, 10, "PAGA", false, "P", "FICT-0001", null, null,
            LocalDateTime.of(2026, 3, 7, 9, 0), null, null, null, null, null);
    private static final CaminhoDaTramitacao SEM_CAMINHO = id -> CaminhoDoFluxo.NENHUM;

    private final List<Collection<Long>> lotesPedidos = new ArrayList<>();

    private final LancamentosDoRequerimento porta = new LancamentosDoRequerimento() {
        @Override
        public LeituraDosLancamentos lerDoRequerimento(long idRequerimento) {
            return new LeituraDosLancamentos(List.of(FALSO, PAGO), false);
        }

        @Override
        public Map<Long, LeituraDosLancamentos> lerEmLote(Collection<Long> ids) {
            lotesPedidos.add(ids);
            return Map.of(10L, new LeituraDosLancamentos(List.of(FALSO, PAGO), false));
        }
    };

    private LerLancamentos ler() {
        return new LerLancamentos(porta, Clock.systemUTC(), SEM_CAMINHO);
    }

    @Test
    void do_requerimento_aplica_a_regra_de_validade() {
        var c = ler().doRequerimento(10);
        assertThat(c.mantidos()).extracting(m -> m.lancamento().id()).containsExactly(2L);
        assertThat(c.ignoradosPorMotivo()).containsEntry(MotivoDoIgnorado.FALSO_SEM_DAR, 1);
    }

    @Test
    void historico_usa_o_relogio_do_projeto() {
        var historico = ler().historicoEmLote(List.of(10L, 20L));
        assertThat(lotesPedidos).hasSize(1);
        assertThat(historico.get(10L).resumo()).isEqualTo(ResumoFinanceiro.QUITADO);
        assertThat(historico.get(20L).resumo()).isEqualTo(ResumoFinanceiro.SEM_TAXA);
    }

    @Test
    void lote_devolve_todo_id_pedido_numa_chamada() {
        var lote = ler().emLote(List.of(10L, 20L, 10L));
        assertThat(lotesPedidos).hasSize(1);
        assertThat(lote).containsOnlyKeys(10L, 20L);
        assertThat(lote.get(10L).mantidos()).hasSize(1);
        assertThat(lote.get(20L).semLancamento()).isTrue();
    }
}
