package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

/** O pedido não tem arquivo: a geração falhou, foi cancelada ou ainda está em andamento. */
public class DossieSemArquivoException extends RastroException {

    public DossieSemArquivoException(String mensagemSegura) {
        super(ErrorCode.DOSSIE_SEM_ARQUIVO, mensagemSegura);
    }
}
