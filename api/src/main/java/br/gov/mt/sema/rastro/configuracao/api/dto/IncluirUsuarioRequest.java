package br.gov.mt.sema.rastro.configuracao.api.dto;

/**
 * @param cpfCnpj com ou sem máscara; obrigatório para o Operador
 * @param senha senha inicial, pela política de senha; nunca sai em resposta, log nem {@code toString}
 */
public record IncluirUsuarioRequest(String nome, String login, String cpfCnpj, String perfil, String senha) {

    @Override
    public String toString() {
        return "IncluirUsuarioRequest[login=" + login + ", perfil=" + perfil + "]";
    }
}
