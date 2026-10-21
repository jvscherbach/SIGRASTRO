package br.gov.mt.sema.vistas.acesso.application;

import java.time.Instant;

public record Sessao(String token, String login, Instant expiraEm) {
}
