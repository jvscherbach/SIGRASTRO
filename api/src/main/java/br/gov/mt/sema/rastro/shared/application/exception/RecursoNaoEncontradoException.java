package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

public class RecursoNaoEncontradoException extends RastroException {

    public RecursoNaoEncontradoException(String mensagemSegura) {
        super(ErrorCode.NAO_ENCONTRADO, mensagemSegura);
    }
}
