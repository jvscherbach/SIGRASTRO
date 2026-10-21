package br.gov.mt.sema.vistas.acesso.domain;

public record Usuario(String login, String nome, Perfil perfil, String cpfCnpj, boolean ativo) {
}
