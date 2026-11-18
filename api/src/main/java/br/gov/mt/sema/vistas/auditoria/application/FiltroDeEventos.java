package br.gov.mt.sema.vistas.auditoria.application;

/**
 * Filtros da lista e do CSV, como vieram do cliente.
 *
 * @param periodo hoje, 7d (padrão), 30d ou todo
 * @param tipo código de {@code TipoDeEvento} do grupo consultado
 * @param usuario login, sem diferenciar maiúsculas
 */
public record FiltroDeEventos(String periodo, String texto, String tipo, String usuario, boolean somenteSigilosos) {
}
