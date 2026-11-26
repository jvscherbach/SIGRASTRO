package br.gov.mt.sema.rastro.acesso.application;

/**
 * Publicado quando uma tentativa de login falha por usuário ou senha inválidos. Não leva login nem senha: a auditoria não
 * pode dizer se o login existe, e o campo de login pode ter recebido uma senha digitada por engano.
 *
 * @param iniciouBloqueio esta falha esgotou o limite e iniciou um bloqueio
 */
public record LoginRecusado(boolean iniciouBloqueio) {
}
