package br.gov.mt.sema.vistas.shared.application.exception;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;

public class RecursoNaoEncontradoException extends VistasException {

    public RecursoNaoEncontradoException(String mensagemSegura) {
        super(ErrorCode.NAO_ENCONTRADO, mensagemSegura);
    }
}
