package br.gov.mt.sema.rastro.acesso.application;

import br.gov.mt.sema.rastro.acesso.domain.Usuario;

/**
 * Publicado quando o próprio usuário troca a senha. Não leva senha nem hash. Quem registra auditoria ouve este evento; o
 * módulo acesso não depende dela.
 */
public record SenhaTrocada(Usuario usuario) {
}
