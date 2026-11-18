package br.gov.mt.sema.vistas.acesso.application;

import java.util.Optional;

import br.gov.mt.sema.vistas.acesso.domain.Usuario;

/**
 * Contrato do módulo acesso para quem prova o acesso sem sessão (ticket de download): o usuário como está agora, com o
 * perfil vigente. Desativado ou removido é vazio.
 */
public interface UsuariosAtivos {

    Optional<Usuario> porLogin(String login);
}
