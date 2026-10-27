package br.gov.mt.sema.vistas.dossie.application.port;

import java.time.LocalDateTime;
import java.util.Optional;

import br.gov.mt.sema.vistas.dossie.domain.TicketDeDownload;

/** Tickets de download emitidos, indexados pelo resumo do valor (D49). No protótipo, em memória (D09). */
public interface TicketsDeDownload {

    void guardar(String resumo, TicketDeDownload ticket);

    /** Remove e devolve o ticket (uso único: uma segunda chamada com o mesmo resumo não encontra nada). */
    Optional<TicketDeDownload> consumir(String resumo);

    /** Esquece os vencidos; devolve quantos saíram. */
    int descartarVencidos(LocalDateTime agora);
}
