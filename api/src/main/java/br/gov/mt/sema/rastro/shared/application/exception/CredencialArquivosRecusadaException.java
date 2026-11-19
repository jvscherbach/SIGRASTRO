package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

/** A API de arquivos recusou o token (401 ou 403). A mensagem nunca traz o valor do token. */
public class CredencialArquivosRecusadaException extends RastroException {

    public static final String MENSAGEM = "Token da API de arquivos expirado ou recusado: capture um novo token.";

    public CredencialArquivosRecusadaException() {
        super(ErrorCode.CREDENCIAL_ARQUIVOS_RECUSADA, MENSAGEM);
    }
}
