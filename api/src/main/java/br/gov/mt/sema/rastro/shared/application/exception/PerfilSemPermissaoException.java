package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

public class PerfilSemPermissaoException extends RastroException {

    public PerfilSemPermissaoException(String mensagemSegura) {
        super(ErrorCode.PERFIL_SEM_PERMISSAO, mensagemSegura);
    }
}
