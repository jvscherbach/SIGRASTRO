package br.gov.mt.sema.rastro.painel.domain;

import java.util.List;

/**
 * Gráfico 2. {@code totalNaFila}: requerimentos do ano fora de concluído, cancelado e arquivado. As três contagens "à parte"
 * nunca somem em silêncio; um requerimento paralelo conta uma vez em cada etapa (a soma das barras pode passar do total).
 */
public record FilaPorEtapa(List<BarraDeEtapa> barras, long foraDoCatalogo, long semInstancia, long etapaNaoMapeada,
        long totalNaFila, List<AvisoDoPainel> avisos) {

    public FilaPorEtapa {
        barras = List.copyOf(barras);
        avisos = List.copyOf(avisos);
    }
}
