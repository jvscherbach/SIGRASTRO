package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

public class ParametroInvalidoException extends RastroException {

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
