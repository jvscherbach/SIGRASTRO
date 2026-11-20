package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.List;

/** Catálogo de etapas com problema de conteúdo. Derruba a subida, com todos os problemas na mensagem (sem dado pessoal). */
public class ModeloDeFluxoInvalido extends RuntimeException {

    private final List<String> problemas;

    public ModeloDeFluxoInvalido(String origem, List<String> problemas) {
        super("Catálogo de etapas inválido (" + origem + "): " + String.join("; ", problemas));
        this.problemas = List.copyOf(problemas);
    }

    public List<String> problemas() {
        return problemas;
    }
}
