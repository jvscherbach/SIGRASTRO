package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

/** O banco do SIGA não está configurado ou não respondeu. A mensagem nunca traz detalhe técnico. */
public class SigaIndisponivelException extends RastroException {

    public SigaIndisponivelException() {
        super(ErrorCode.SIGA_INDISPONIVEL, "O SIGA está indisponível no momento. Tente novamente em instantes.");
    }
}
