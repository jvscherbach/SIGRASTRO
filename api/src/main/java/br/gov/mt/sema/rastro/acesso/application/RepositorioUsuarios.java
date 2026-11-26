package br.gov.mt.sema.rastro.acesso.application;

import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.domain.Usuario;

public interface RepositorioUsuarios {

    /** O usuário pelo login, ativo ou não. A conferência da senha é de {@link AutenticarUsuario}. */
    Optional<Usuario> buscarPorLogin(String login);

    /** Entrada bem-sucedida: atualiza o último acesso. */
    default void registrarAcesso(String login) {
    }
}
