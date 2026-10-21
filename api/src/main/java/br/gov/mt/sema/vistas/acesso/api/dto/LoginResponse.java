package br.gov.mt.sema.vistas.acesso.api.dto;

import java.time.OffsetDateTime;

public record LoginResponse(String token, String tipo, OffsetDateTime expiraEm, UsuarioResponse usuario) {
}
