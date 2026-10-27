package br.gov.mt.sema.vistas.documento.application;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;
import br.gov.mt.sema.vistas.shared.application.exception.VistasException;

/** Falha ao entregar o conteúdo para o modal (obtenção, conversão, fila cheia), já com o código e a mensagem exibível. */
public class FalhaNoConteudoException extends VistasException {

    public FalhaNoConteudoException(ErrorCode errorCode, String mensagemSegura) {
        super(errorCode, mensagemSegura);
    }
}
