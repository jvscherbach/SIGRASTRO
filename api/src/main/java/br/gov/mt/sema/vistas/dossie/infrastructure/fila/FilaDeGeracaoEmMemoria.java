package br.gov.mt.sema.vistas.dossie.infrastructure.fila;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.vistas.dossie.application.port.FilaDeGeracao;
import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fila em memória com executor limitado a DOSSIE_THREADS gerações simultâneas (padrão 2). Cada geração baixa os
 * arquivos em sequência, então o total de downloads simultâneos nunca passa desse limite. Pool separado do
 * agendador (docs/padroes/agendamento.md).
 */
@Component
class FilaDeGeracaoEmMemoria implements FilaDeGeracao {

    private static final Logger log = LoggerFactory.getLogger(FilaDeGeracaoEmMemoria.class);

    private final ThreadPoolExecutor executor;

    FilaDeGeracaoEmMemoria(ParametrosDoDossie parametros) {
        int threads = Math.max(1, parametros.threads());
        AtomicInteger contador = new AtomicInteger();
        ThreadFactory fabrica = tarefa -> {
            Thread thread = new Thread(tarefa, "dossie-" + contador.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                fabrica);
    }

    @Override
    public void submeter(Runnable geracao) {
        executor.execute(() -> {
            try {
                geracao.run();
            } catch (RuntimeException e) {
                log.error("Geração de dossiê terminou com erro não tratado", e);
            }
        });
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }
}
