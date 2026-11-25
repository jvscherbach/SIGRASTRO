package br.gov.mt.sema.rastro.painel.domain;

/** Uma barra do gráfico 2: a etapa do catálogo (de um modelo) e quantos requerimentos estão nela agora. */
public record BarraDeEtapa(String modelo, String idDaEtapa, String rotulo, long total) {
}
