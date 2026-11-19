package br.gov.mt.sema.rastro.auditoria.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.rastro.auditoria.domain.GrupoDeEvento;

/** Leitura dos eventos. Interna à auditoria: quem aplica perfil, vínculo e mascaramento é o caso de uso da consulta. */
public interface ConsultarEventos {

    /**
     * Eventos do grupo, do mais recente para o mais antigo, sob demanda (serve ao CSV sem montar tudo em memória).
     *
     * @param desde início, inclusive; {@code null} = sem início
     * @param ate fim, exclusive; {@code null} = sem fim
     */
    Stream<EventoDeAuditoria> doPeriodo(GrupoDeEvento grupo, Instant desde, Instant ate);

    Optional<EventoDeAuditoria> porId(GrupoDeEvento grupo, long id);
}
