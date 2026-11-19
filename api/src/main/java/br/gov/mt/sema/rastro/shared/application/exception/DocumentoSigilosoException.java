package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

/** RF20: documento sigiloso solicitado por perfil sem permissão. A mensagem não traz título nem nome do arquivo. */
public class DocumentoSigilosoException extends RastroException {

    public DocumentoSigilosoException() {
        super(ErrorCode.DOCUMENTO_SIGILOSO, "A seleção contém documento sigiloso, que o seu perfil não pode incluir.");
    }
}
