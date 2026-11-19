package br.gov.mt.sema.rastro.auditoria.application;

/** Totais dos eventos que o usuário vê no período, antes dos demais filtros (docs/11). */
public record IndicadoresDoPeriodo(long dossiesGerados, long eventosComSigilosos, long entradas, long alteracoes,
        long usuarios) {
}
