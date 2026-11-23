package br.gov.mt.sema.rastro.painel.domain;

import java.util.List;

/**
 * Os requerimentos do ano lidos para a fila e a carga (gráficos 2 e 3), em ordem de {@code ID}, até o teto
 * ({@code rastro.painelLimiteRequerimentos}). {@code truncado}: havia mais do que o teto; só os primeiros vieram e os
 * gráficos 2 e 3 ficam parciais (o gráfico 1 não depende do teto).
 */
public record BaseDoAno(List<RequerimentoDaBase> requerimentos, boolean truncado) {

    public BaseDoAno {
        requerimentos = List.copyOf(requerimentos);
    }
}
