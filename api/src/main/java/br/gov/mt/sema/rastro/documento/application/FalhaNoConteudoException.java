package br.gov.mt.sema.rastro.documento.application;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import br.gov.mt.sema.rastro.shared.application.exception.RastroException;

/** Falha ao entregar o conteúdo para o modal (obtenção, conversão, fila cheia), já com o código e a mensagem exibível. */
public class FalhaNoConteudoException extends RastroException {

    public FalhaNoConteudoException(ErrorCode errorCode, String mensagemSegura) {
        super(errorCode, mensagemSegura);
    }
}
