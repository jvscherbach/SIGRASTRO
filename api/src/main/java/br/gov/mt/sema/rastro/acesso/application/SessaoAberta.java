package br.gov.mt.sema.rastro.acesso.application;

import java.time.Instant;

import br.gov.mt.sema.rastro.acesso.domain.Usuario;

public record SessaoAberta(String token, Instant expiraEm, Usuario usuario) {
}
