package br.gov.mt.sema.rastro.auditoria.application;

import java.util.List;

import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;

/** Eventos já filtrados por perfil e mascarados. */
public record PaginaDeEventos(List<EventoDeAuditoria> itens, int pagina, int tamanho, long total,
        IndicadoresDoPeriodo indicadores) {
}
