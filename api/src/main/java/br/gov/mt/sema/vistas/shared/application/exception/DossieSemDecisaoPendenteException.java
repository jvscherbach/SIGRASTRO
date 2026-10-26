package br.gov.mt.sema.vistas.shared.application.exception;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;

/** Continuação pedida para um dossiê que não está em PENDENTE_DECISAO (S2.2). */
public class DossieSemDecisaoPendenteException extends VistasException {

    public DossieSemDecisaoPendenteException(String mensagemSegura) {
        super(ErrorCode.DOSSIE_SEM_DECISAO_PENDENTE, mensagemSegura);
    }
}
