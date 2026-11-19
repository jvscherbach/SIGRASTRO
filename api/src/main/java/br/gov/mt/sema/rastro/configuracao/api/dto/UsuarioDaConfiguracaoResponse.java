package br.gov.mt.sema.rastro.configuracao.api.dto;

import java.time.Instant;

import br.gov.mt.sema.rastro.acesso.application.UsuarioAdministrado;

/**
 * Linha da aba de usuários.
 *
 * @param id o login, que é único e não muda
 * @param cpfCnpj só mascarado; {@code null} sem cadastro
 */
public record UsuarioDaConfiguracaoResponse(String id, String nome, String login, String perfil, String perfilRotulo,
        boolean ativo, Instant ultimoAcesso, String cpfCnpj) {

    public static UsuarioDaConfiguracaoResponse de(UsuarioAdministrado usuario) {
        return new UsuarioDaConfiguracaoResponse(usuario.login(), usuario.nome(), usuario.login(), usuario.perfil(),
                usuario.perfilRotulo(), usuario.ativo(), usuario.ultimoAcesso(), usuario.cpfCnpjMascarado());
    }
}
