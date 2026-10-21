package br.gov.mt.sema.vistas.acesso.application;

import java.time.Instant;

import br.gov.mt.sema.vistas.acesso.domain.Usuario;

public record SessaoAberta(String token, Instant expiraEm, Usuario usuario) {
}
