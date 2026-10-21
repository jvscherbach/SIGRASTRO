package br.gov.mt.sema.vistas.shared.application.exception;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;

/** O banco do SIGA não está configurado ou não respondeu. A mensagem nunca traz detalhe técnico. */
public class SigaIndisponivelException extends VistasException {

    public SigaIndisponivelException() {
        super(ErrorCode.SIGA_INDISPONIVEL, "O SIGA está indisponível no momento. Tente novamente em instantes.");
    }
}
