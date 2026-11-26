package br.gov.mt.sema.rastro.caixa.infrastructure.memoria;

import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.A1;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.A2;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import br.gov.mt.sema.rastro.caixa.domain.EstadoDePosse;
import org.junit.jupiter.api.Test;

/** O visto em memória: gravação atômica por analista, independente entre analistas (S11, bloco 5). */
class VistoDoAnalistaEmMemoriaTest {

    private static final EstadoDePosse COM_ANALISTA = EstadoDePosse.comAnalista("analise_do_processo");

    @Test
    void grava_so_se_ausente_e_cada_analista_tem_o_seu() {
        VistoDoAnalistaEmMemoria visto = new VistoDoAnalistaEmMemoria();
        assertThat(visto.ultimo(A1)).isEmpty();
        assertThat(visto.gravarSeAusente(A1, Map.of(1L, COM_ANALISTA))).isTrue();
        assertThat(visto.gravarSeAusente(A1, Map.of(2L, COM_ANALISTA))).isFalse();
        assertThat(visto.ultimo(A1).orElseThrow()).containsOnlyKeys(1L);
        assertThat(visto.ultimo(A2)).isEmpty();
    }

    @Test
    void atualizacoes_simultaneas_de_varias_abas_nao_se_perdem() throws Exception {
        VistoDoAnalistaEmMemoria visto = new VistoDoAnalistaEmMemoria();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        IntStream.range(0, 200).forEach(i -> pool.execute(() -> visto.atualizar(A1, atual -> {
            Map<Long, EstadoDePosse> novo = new HashMap<>(atual);
            novo.put((long) i, COM_ANALISTA);
            return novo;
        })));
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(visto.ultimo(A1).orElseThrow()).hasSize(200);
    }
}
