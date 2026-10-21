package br.gov.mt.sema.vistas.acesso.api.dto;

import br.gov.mt.sema.vistas.acesso.domain.Perfil;
import br.gov.mt.sema.vistas.acesso.domain.Usuario;

public record UsuarioResponse(String login, String nome, Perfil perfil, String perfilDescricao, String cpfCnpj,
        PermissoesResponse permissoes) {

    public static UsuarioResponse de(Usuario usuario) {
        Perfil perfil = usuario.perfil();
        return new UsuarioResponse(usuario.login(), usuario.nome(), perfil, perfil.rotulo(), usuario.cpfCnpj(),
                new PermissoesResponse(perfil.podeVerSigilosos(), perfil.podeVerAuditoriaAcessos(),
                        perfil.podeAcessarConfiguracoes(), perfil.restritoAoInteressado(),
                        perfil.podeVerResponsavelTarefa()));
    }
}
