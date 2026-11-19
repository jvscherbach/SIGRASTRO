package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

public class NaoAutenticadoException extends RastroException {

    public NaoAutenticadoException(String mensagemSegura) {
        super(ErrorCode.NAO_AUTENTICADO, mensagemSegura);
    }
}
