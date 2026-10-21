package br.gov.mt.sema.vistas.shared.application.exception;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;

/** Exceção base com código estável e mensagem segura para exibição ao usuário. */
public class VistasException extends RuntimeException {

    private final ErrorCode errorCode;

    public VistasException(ErrorCode errorCode, String mensagemSegura) {
        super(mensagemSegura);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
