package br.gov.mt.sema.vistas.auditoria.api.dto;

import br.gov.mt.sema.vistas.auditoria.domain.UsuarioDoEvento;

public record UsuarioDoEventoResponse(String login, String nome, String perfil) {

    static UsuarioDoEventoResponse de(UsuarioDoEvento usuario) {
        return new UsuarioDoEventoResponse(usuario.login(), usuario.nome(), usuario.perfil());
    }
}
