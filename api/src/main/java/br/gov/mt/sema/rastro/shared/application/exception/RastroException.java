package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

/** Exceção base com código estável e mensagem segura para exibição ao usuário. */
public class RastroException extends RuntimeException {

    private final ErrorCode errorCode;

    public RastroException(ErrorCode errorCode, String mensagemSegura) {
        super(mensagemSegura);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
