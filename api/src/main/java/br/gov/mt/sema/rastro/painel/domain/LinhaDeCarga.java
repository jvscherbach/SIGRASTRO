package br.gov.mt.sema.rastro.painel.domain;

/** Uma barra do gráfico 3. Só rótulo e total: o tipo não tem onde guardar CPF. */
public record LinhaDeCarga(TipoDeCarga tipo, String rotulo, long total) {
}
