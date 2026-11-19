package br.gov.mt.sema.rastro.dossie.api.dto;

import java.time.LocalDateTime;

/** Ticket de uso único para o download direto (D49). Só esta resposta traz o valor. */
public record TicketResponse(String ticket, LocalDateTime expiraEm) {

    @Override
    public String toString() {
        return "TicketResponse[expiraEm=" + expiraEm + "]";
    }
}
