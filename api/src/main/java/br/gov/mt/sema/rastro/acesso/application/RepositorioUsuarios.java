package br.gov.mt.sema.rastro.acesso.application;

import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.domain.Usuario;

public interface RepositorioUsuarios {

    /** Devolve o usuário somente se o login existe e a senha confere. */
    Optional<Usuario> autenticar(String login, String senha);

    Optional<Usuario> buscarPorLogin(String login);

    /** Entrada bem-sucedida: atualiza o último acesso. */
    default void registrarAcesso(String login) {
    }
}
