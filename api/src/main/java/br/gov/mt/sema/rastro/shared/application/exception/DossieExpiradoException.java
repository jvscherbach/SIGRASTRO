package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

/** O dossiê venceu ou já foi baixado: o arquivo foi apagado (RNF05). */
public class DossieExpiradoException extends RastroException {

    public DossieExpiradoException() {
        super(ErrorCode.DOSSIE_EXPIRADO, "O dossiê expirou e o arquivo foi removido. Gere-o novamente.");
    }
}
