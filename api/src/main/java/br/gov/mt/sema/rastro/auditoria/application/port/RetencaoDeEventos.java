package br.gov.mt.sema.rastro.auditoria.application.port;

/** Único descarte de eventos, separado de {@link RepositorioDeEventos} e usado só pela limpeza agendada. */
public interface RetencaoDeEventos {

    /** Descarta os eventos além da retenção do grupo e devolve quantos saíram. */
    int aplicar();
}
