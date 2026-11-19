package br.gov.mt.sema.rastro.acesso.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Informe o usuário.") @Size(max = 100, message = "Máximo de 100 caracteres.") String usuario,
        @NotBlank(message = "Informe a senha.") @Size(max = 200, message = "Máximo de 200 caracteres.") String senha) {
}
