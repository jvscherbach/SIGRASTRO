package br.gov.mt.sema.rastro.configuracao.api.dto;

/**
 * O campo ausente fica como está.
 *
 * @param senha redefine a senha (pela política de senha); nunca sai em resposta, log nem {@code toString}
 */
public record AlterarUsuarioRequest(String perfil, Boolean ativo, String senha) {

    @Override
    public String toString() {
        return "AlterarUsuarioRequest[perfil=" + perfil + ", ativo=" + ativo + "]";
    }
}
