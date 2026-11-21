package br.gov.mt.sema.rastro.tramitacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Limite de leituras simultâneas ao Camunda (S8, bloco 7). */
class LimiteDeLeiturasDoCamundaTest {

    private final ParametrosDoRastroDeTeste parametros = new ParametrosDoRastroDeTeste();
    private final LimiteDeLeiturasDoCamunda limite = new LimiteDeLeiturasDoCamunda(parametros);

    @Test
    void nunca_passa_do_teto_e_todos_terminam() throws Exception {
        parametros.leiturasSimultaneas = 3;
        AtomicInteger agora = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return limite.executar(Duration.ofSeconds(10), () -> {
                        maximo.accumulateAndGet(agora.incrementAndGet(), Math::max);
                        dormir(50);
                        return agora.decrementAndGet();
                    });
                }));
            }
            largada.countDown();
            for (Future<Integer> f : futuros) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(maximo.get()).isEqualTo(3);
        assertThat(limite.maiorUso()).isEqualTo(3);
    }

    @Test
    void sem_vaga_dentro_da_espera_lanca_e_a_vaga_volta_depois_de_falha() throws Exception {
        parametros.leiturasSimultaneas = 1;
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch ocupou = new CountDownLatch(1);
        try {
            Future<?> longa = pool.submit(() -> limite.executar(Duration.ofSeconds(5), () -> {
                ocupou.countDown();
                dormir(500);
                return null;
            }));
            ocupou.await();
            assertThatThrownBy(() -> limite.executar(Duration.ofMillis(100), () -> 1))
                    .isInstanceOf(LimiteDeLeiturasDoCamunda.SemVagaNoCamunda.class);
            longa.get();
        } finally {
            pool.shutdownNow();
        }
        assertThatThrownBy(() -> limite.executar(Duration.ofSeconds(1), () -> {
            throw new IllegalStateException("falha da leitura");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(limite.executar(Duration.ofMillis(10), () -> 7)).isEqualTo(7);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
