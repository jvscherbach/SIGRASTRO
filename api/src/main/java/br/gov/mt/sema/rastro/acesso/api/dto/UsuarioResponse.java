package br.gov.mt.sema.rastro.acesso.api.dto;

import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;

public record UsuarioResponse(String login, String nome, Perfil perfil, String perfilDescricao, String cpfCnpj,
        PermissoesResponse permissoes) {

    public static UsuarioResponse de(Usuario usuario) {
        Perfil perfil = usuario.perfil();
        // S11: o CPF do Analista é o vínculo com o Camunda e nunca sai em resposta, nem mascarado
        String cpfCnpj = perfil.podeVerCaixa() ? null : usuario.cpfCnpj();
        return new UsuarioResponse(usuario.login(), usuario.nome(), perfil, perfil.rotulo(), cpfCnpj,
                new PermissoesResponse(perfil.podeVerSigilosos(), perfil.podeVerAuditoriaAcessos(),
                        perfil.podeAcessarConfiguracoes(), perfil.restritoAoInteressado(),
                        perfil.podeVerResponsavelTarefa(), perfil.podeVerPainel(), perfil.podeVerCaixa()));
    }
}
