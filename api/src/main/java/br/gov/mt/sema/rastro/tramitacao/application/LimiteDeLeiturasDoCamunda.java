package br.gov.mt.sema.rastro.tramitacao.application;

import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import org.springframework.stereotype.Component;

/**
 * Limite de leituras simultâneas ao Camunda nesta instância do Rastro (S8, bloco 7): a posição (lista da busca, outros
 * requerimentos do processo, painel) e a tramitação passam por aqui, para que muitos pedidos ao mesmo tempo não virem
 * muitas consultas ao Camunda de produção. O teto é {@code RASTRO_CAMUNDA_LEITURAS_SIMULTANEAS}, lido a cada pedido de
 * vaga (uma mudança vale no próximo).
 *
 * <p>Quem não consegue vaga dentro da espera recebe {@link SemVagaNoCamunda}; o chamador transforma em
 * {@code INDISPONIVEL}, como qualquer falha do Camunda.
 */
@Component
public class LimiteDeLeiturasDoCamunda {

    private final ParametrosDoRastro parametros;
    private final ReentrantLock trava = new ReentrantLock(true);
    private final Condition liberou = trava.newCondition();
    private int emUso;
    private int maiorUso;

    public LimiteDeLeiturasDoCamunda(ParametrosDoRastro parametros) {
        this.parametros = parametros;
    }

    /** Executa a leitura com uma vaga; espera por ela no máximo {@code espera}. */
    public <T> T executar(Duration espera, Supplier<T> leitura) {
        ocupar(espera);
        try {
            return leitura.get();
        } finally {
            liberar();
        }
    }

    private void ocupar(Duration espera) {
        long restante = Math.max(0, espera.toNanos());
        trava.lock();
        try {
            while (emUso >= Math.max(1, parametros.leiturasSimultaneasDoCamunda())) {
                if (restante <= 0) {
                    throw new SemVagaNoCamunda();
                }
                restante = liberou.awaitNanos(restante);
            }
            emUso++;
            maiorUso = Math.max(maiorUso, emUso);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SemVagaNoCamunda();
        } finally {
            trava.unlock();
        }
    }

    private void liberar() {
        trava.lock();
        try {
            emUso--;
            liberou.signalAll();
        } finally {
            trava.unlock();
        }
    }

    /** Para teste e diagnóstico: o maior número de leituras ao mesmo tempo desde a subida. */
    int maiorUso() {
        trava.lock();
        try {
            return maiorUso;
        } finally {
            trava.unlock();
        }
    }

    /** Sem vaga dentro da espera: o Camunda está ocupado com outras leituras deste Rastro. */
    public static final class SemVagaNoCamunda extends RuntimeException {
        SemVagaNoCamunda() {
            super("sem vaga para ler o Camunda", null, false, false);
        }
    }

}
