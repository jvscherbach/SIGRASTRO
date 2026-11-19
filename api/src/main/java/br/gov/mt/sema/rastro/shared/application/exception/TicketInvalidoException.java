package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

/** Ticket de download inexistente, já usado, vencido, de outro pedido ou de outro usuário (D49). Sem detalhe do motivo. */
public class TicketInvalidoException extends RastroException {

    public TicketInvalidoException() {
        super(ErrorCode.TICKET_INVALIDO, "Link de download inválido, já usado ou expirado. Peça o download de novo.");
    }
}
