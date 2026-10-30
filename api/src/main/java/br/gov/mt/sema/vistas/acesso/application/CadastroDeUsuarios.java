package br.gov.mt.sema.vistas.acesso.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.vistas.acesso.domain.Usuario;

/** Escrita do cadastro de usuários, usada só por {@link AdministrarUsuarios}. */
public interface CadastroDeUsuarios extends RepositorioUsuarios {

    List<Usuario> listar();

    /** Inclui ou substitui pelo login. */
    void salvar(Usuario usuario);

    Optional<Instant> ultimoAcesso(String login);
}
