package br.gov.mt.sema.vistas.shared.application.exception;

import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;

public class PerfilSemPermissaoException extends VistasException {

    public PerfilSemPermissaoException(String mensagemSegura) {
        super(ErrorCode.PERFIL_SEM_PERMISSAO, mensagemSegura);
    }
}
