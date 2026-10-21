package br.gov.mt.sema.vistas.shared.application.exception;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;

public class NaoAutenticadoException extends VistasException {

    public NaoAutenticadoException(String mensagemSegura) {
        super(ErrorCode.NAO_AUTENTICADO, mensagemSegura);
    }
}
