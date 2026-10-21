package br.gov.mt.sema.vistas.shared.application.exception;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;

/** RN07: o Operador só acessa processos e requerimentos em que é o interessado. */
public class NaoInteressadoException extends VistasException {

    public NaoInteressadoException() {
        super(ErrorCode.NAO_INTERESSADO, "Você não é o interessado deste processo ou requerimento.");
    }
}
