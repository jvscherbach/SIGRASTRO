package br.gov.mt.sema.rastro.configuracao.api.dto;

/** @param cpfCnpj com ou sem máscara; obrigatório para o Operador */
public record IncluirUsuarioRequest(String nome, String login, String cpfCnpj, String perfil) {
}
