package br.gov.mt.sema.rastro.tramitacao.infrastructure.memoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import org.junit.jupiter.api.Test;

/** Cache da tramitação (S8, bloco 7): validade, versão do catálogo na chave, indisponível fora e teto de entradas. */
class CacheDaTramitacaoEmMemoriaTest {

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
    private final CacheDaTramitacaoEmMemoria cache = new CacheDaTramitacaoEmMemoria(relogio);
    private final TramitacaoDoRequerimento naoSuportado = TramitacaoDoRequerimento.naoSuportado(Optional.empty());

    @Test
    void vale_ate_a_validade_e_depois_some() {
        cache.guardar(8001, "v1", naoSuportado, Duration.ofSeconds(60));
        relogio.agora = relogio.agora.plusSeconds(59);
        assertThat(cache.obter(8001, "v1")).contains(naoSuportado);
        relogio.agora = relogio.agora.plusSeconds(1);
        assertThat(cache.obter(8001, "v1")).isEmpty();
        assertThat(cache.tamanho()).isZero();
    }

    @Test
    void versao_do_catalogo_faz_parte_da_chave() {
        cache.guardar(8001, "v1", naoSuportado, Duration.ofSeconds(60));
        assertThat(cache.obter(8001, "v2")).isEmpty();
        assertThat(cache.obter(8002, "v1")).isEmpty();
    }

    @Test
    void indisponivel_e_validade_zero_nao_entram() {
        cache.guardar(8001, "v1", TramitacaoDoRequerimento.indisponivel(Optional.empty(), Optional.empty()),
                Duration.ofSeconds(60));
        cache.guardar(8002, "v1", naoSuportado, Duration.ZERO);
        assertThat(cache.tamanho()).isZero();
    }

    @Test
    void teto_de_entradas_e_respeitado() {
        for (int i = 0; i < CacheDaTramitacaoEmMemoria.MAXIMO_DE_ENTRADAS + 50; i++) {
            cache.guardar(i, "v1", naoSuportado, Duration.ofSeconds(60));
        }
        assertThat(cache.tamanho()).isLessThanOrEqualTo(CacheDaTramitacaoEmMemoria.MAXIMO_DE_ENTRADAS);
    }
}
