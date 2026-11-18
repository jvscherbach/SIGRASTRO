package br.gov.mt.sema.vistas.auditoria.application.port;

import br.gov.mt.sema.vistas.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.vistas.auditoria.domain.NovoEvento;

/**
 * Somente inclusão (docs/11): esta interface não oferece alterar nem apagar. O repositório numera e data o evento, para
 * a ordem do id acompanhar a do tempo. Só a {@link RetencaoDeEventos} descarta.
 */
public interface RepositorioDeEventos extends ConsultarEventos {

    /** @throws IllegalArgumentException se o evento for inválido; nada é incluído */
    EventoDeAuditoria incluir(NovoEvento evento);
}
