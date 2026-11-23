package br.gov.mt.sema.rastro.financeiro.domain;

/** Lançamento fora da lista pela regra de validade, com o motivo (só para o diagnóstico do Administrador). */
public record LancamentoIgnorado(Lancamento lancamento, MotivoDoIgnorado motivo) {
}
