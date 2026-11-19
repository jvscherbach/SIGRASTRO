package br.gov.mt.sema.rastro.dossie.infrastructure.fila;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.dossie.application.port.ParametrosDoDossie;
import org.junit.jupiter.api.Test;

class FilaDeGeracaoEmMemoriaTest {

    private static ParametrosDoDossie comThreads(int threads) {
        return new ParametrosDoDossie() {
            @Override
            public int ttlMinutos() {
                return 30;
            }

            @Override
            public int threads() {
                return threads;
            }

            @Override
            public int downloadTotalMaximoSegundos() {
                return 900;
            }

            @Override
            public long espacoMinimoMb() {
                return 0;
            }

            @Override
            public int downloadTimeoutSegundos() {
                return 60;
            }

            @Override
            public boolean sumario() {
                return true;
            }

            @Override
            public boolean marcadores() {
                return true;
            }

            @Override
            public boolean numeracao() {
                return true;
            }

            @Override
            public Path diretorio() {
                return Path.of("unused");
            }
        };
    }

    /** Submete 6 gerações lentas e devolve a maior quantidade que rodou ao mesmo tempo. */
    private static int maximoSimultaneo(int threads) throws InterruptedException {
        FilaDeGeracaoEmMemoria fila = new FilaDeGeracaoEmMemoria(comThreads(threads));
        AtomicInteger rodando = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        CountDownLatch fim = new CountDownLatch(6);
        try {
            for (int i = 0; i < 6; i++) {
                fila.submeter(() -> {
                    int agora = rodando.incrementAndGet();
                    maximo.accumulateAndGet(agora, Math::max);
                    try {
                        Thread.sleep(80);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        rodando.decrementAndGet();
                        fim.countDown();
                    }
                });
            }
            assertThat(fim.await(10, TimeUnit.SECONDS)).isTrue();
            return maximo.get();
        } finally {
            fila.encerrar();
        }
    }

    @Test
    void comUmaThreadUmaGeracaoPorVez() throws InterruptedException {
        assertThat(maximoSimultaneo(1)).isEqualTo(1);
    }

    @Test
    void comDuasThreadsNuncaPassaDeDuas() throws InterruptedException {
        assertThat(maximoSimultaneo(2)).isBetween(1, 2);
    }

    @Test
    void excecaoNaGeracaoNaoDerrubaAFila() throws InterruptedException {
        FilaDeGeracaoEmMemoria fila = new FilaDeGeracaoEmMemoria(comThreads(1));
        CountDownLatch executou = new CountDownLatch(1);
        try {
            fila.submeter(() -> {
                throw new IllegalStateException("falha de teste");
            });
            fila.submeter(executou::countDown);

            assertThat(executou.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            fila.encerrar();
        }
    }
}
