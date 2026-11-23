package br.gov.mt.sema.rastro.painel.domain;

import java.util.Optional;

/** Situações que tiram o requerimento da fila: concluído, cancelado (qualquer motivo) e arquivado (qualquer tipo). */
final class SituacoesForaDaFila {

    private SituacoesForaDaFila() {
    }

    static boolean estaForaDaFila(Optional<String> situacao) {
        String s = situacao.map(String::trim).orElse("");
        return s.equals("CONCLUIDO") || s.startsWith("CANCELADO") || s.startsWith("ARQUIVADO");
    }
}
