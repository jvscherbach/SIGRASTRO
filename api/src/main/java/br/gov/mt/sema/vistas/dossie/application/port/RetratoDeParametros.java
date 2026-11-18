package br.gov.mt.sema.vistas.dossie.application.port;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Um pedido não muda de comportamento no meio: os parâmetros de classe "próxima geração" são retratados na criação e tudo
 * o que a geração lê, nesta thread, vem do retrato.
 */
public interface RetratoDeParametros {

    Map<String, String> retratar();

    <T> T comRetrato(Map<String, String> retrato, Supplier<T> tarefa);

    default void executarComRetrato(Map<String, String> retrato, Runnable tarefa) {
        comRetrato(retrato, () -> {
            tarefa.run();
            return null;
        });
    }
}
