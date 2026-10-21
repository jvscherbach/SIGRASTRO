package br.gov.mt.sema.vistas.acesso.application;

import java.util.Optional;

import br.gov.mt.sema.vistas.acesso.domain.Usuario;

public interface RepositorioUsuarios {

    /** Devolve o usuário somente se o login existe e a senha confere. */
    Optional<Usuario> autenticar(String login, String senha);

    Optional<Usuario> buscarPorLogin(String login);
}
