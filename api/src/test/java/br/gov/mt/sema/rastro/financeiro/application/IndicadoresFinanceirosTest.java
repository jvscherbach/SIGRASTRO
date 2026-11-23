package br.gov.mt.sema.rastro.financeiro.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.domain.CaminhoDoFluxo;
import br.gov.mt.sema.rastro.financeiro.domain.Lancamento;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import br.gov.mt.sema.rastro.financeiro.domain.ResumoFinanceiro;
import org.junit.jupiter.api.Test;

/** Indicador da lista da busca (S9, bloco 5): uma chamada em lote, sem tramitação, e falha sem erro. */
class IndicadoresFinanceirosTest {

    private final AtomicInteger lotes = new AtomicInteger();
    private final AtomicInteger caminhos = new AtomicInteger();

    private IndicadoresFinanceiros com(boolean falhar) {
        LancamentosDoRequerimento porta = new LancamentosDoRequerimento() {
            @Override
            public LeituraDosLancamentos lerDoRequerimento(long id) {
                throw new AssertionError("a lista nunca lê requerimento a requerimento");
            }

            @Override
            public Map<Long, LeituraDosLancamentos> lerEmLote(Collection<Long> ids) {
                lotes.incrementAndGet();
                if (falhar) {
                    throw new IllegalStateException("banco fora");
                }
                return Map.of(1L, new LeituraDosLancamentos(List.of(new Lancamento(1, 1, "PAGA", false, "P", "FICT-0001",
                        LocalDateTime.of(2026, 3, 4, 9, 0), null, LocalDateTime.of(2026, 3, 5, 9, 0), null, null, null,
                        null, null)), false));
            }
        };
        return new IndicadoresFinanceiros(new LerLancamentos(porta, Clock.systemUTC(), id -> {
            caminhos.incrementAndGet();
            return CaminhoDoFluxo.NENHUM;
        }));
    }

    @Test
    void resumo_de_cada_requerimento_numa_chamada_sem_tramitacao() {
        Map<Long, ResumoFinanceiro> r = com(false).resumos(LongStream.rangeClosed(1, 20).boxed().toList());
        assertThat(lotes).hasValue(1);
        assertThat(caminhos).hasValue(0);
        assertThat(r).hasSize(20).containsEntry(1L, ResumoFinanceiro.QUITADO).containsEntry(2L, ResumoFinanceiro.SEM_TAXA);
    }

    @Test
    void falha_do_banco_deixa_a_lista_sem_indicador() {
        assertThat(com(true).resumos(List.of(1L, 2L))).isEmpty();
    }

    @Test
    void lista_vazia_nao_consulta() {
        assertThat(com(false).resumos(List.of())).isEmpty();
        assertThat(lotes).hasValue(0);
    }
}
