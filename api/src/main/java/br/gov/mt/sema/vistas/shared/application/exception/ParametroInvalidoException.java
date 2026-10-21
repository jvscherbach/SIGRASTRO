package br.gov.mt.sema.vistas.shared.application.exception;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;

public class ParametroInvalidoException extends VistasException {

    public ParametroInvalidoException(String mensagemSegura) {
        super(ErrorCode.PARAMETRO_INVALIDO, mensagemSegura);
    }
}
