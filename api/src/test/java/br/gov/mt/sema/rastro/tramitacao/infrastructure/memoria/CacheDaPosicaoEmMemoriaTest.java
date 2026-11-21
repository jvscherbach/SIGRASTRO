package br.gov.mt.sema.rastro.tramitacao.infrastructure.memoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import org.junit.jupiter.api.Test;

/** Cache da posição (S7, bloco 4): validade, versão do catálogo na chave e teto de entradas. */
class CacheDaPosicaoEmMemoriaTest {

    private static final class Relogio extends Clock {
        Instant agora = Instant.parse("2026-03-09T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final Relogio relogio = new Relogio();
    private final CacheDaPosicaoEmMemoria cache = new CacheDaPosicaoEmMemoria(relogio);
    private final PosicaoAtual posicao = PosicaoAtual.naoSuportado();

    @Test
    void vale_ate_a_validade_e_depois_some() {
        cache.guardar(7001, "v1", posicao, Duration.ofSeconds(30));
        relogio.agora = relogio.agora.plusSeconds(29);
        assertThat(cache.obter(7001, "v1")).contains(posicao);
        relogio.agora = relogio.agora.plusSeconds(1);
        assertThat(cache.obter(7001, "v1")).isEmpty();
        assertThat(cache.tamanho()).isZero();
    }

    @Test
    void outra_versao_do_catalogo_nao_serve_o_resultado_antigo() {
        cache.guardar(7001, "v1", posicao, Duration.ofSeconds(30));
        assertThat(cache.obter(7001, "v2")).isEmpty();
        assertThat(cache.obter(7002, "v1")).isEmpty();
    }

    @Test
    void validade_zero_nao_guarda() {
        cache.guardar(7001, "v1", posicao, Duration.ZERO);
        assertThat(cache.obter(7001, "v1")).isEmpty();
    }

    @Test
    void acima_do_teto_limpa_antes_de_guardar() {
        for (int i = 0; i < CacheDaPosicaoEmMemoria.MAXIMO_DE_ENTRADAS + 5; i++) {
            cache.guardar(i, "v1", posicao, Duration.ofSeconds(30));
        }
        assertThat(cache.tamanho()).isLessThanOrEqualTo(CacheDaPosicaoEmMemoria.MAXIMO_DE_ENTRADAS);
        assertThat(cache.obter(CacheDaPosicaoEmMemoria.MAXIMO_DE_ENTRADAS + 4, "v1")).contains(posicao);
    }
}
