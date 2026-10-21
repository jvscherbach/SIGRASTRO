package br.gov.mt.sema.vistas.shared.application.exception;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;

public class ParametroInvalidoException extends VistasException {

    private final String campo;

    public ParametroInvalidoException(String mensagemSegura) {
        this(null, mensagemSegura);
    }

    /** Indica o parâmetro inválido; o handler o devolve na lista de campos do ProblemDetail. */
    public ParametroInvalidoException(String campo, String mensagemSegura) {
        super(ErrorCode.PARAMETRO_INVALIDO, mensagemSegura);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
