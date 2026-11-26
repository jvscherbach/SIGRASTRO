package br.gov.mt.sema.rastro.acesso.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Troca da própria senha. Os valores nunca saem em {@code toString}. */
public record TrocarSenhaRequest(
        @NotBlank(message = "Informe a senha atual.") @Size(max = 200, message = "Máximo de 200 caracteres.") String senhaAtual,
        @NotBlank(message = "Informe a nova senha.") @Size(max = 200, message = "Máximo de 200 caracteres.") String novaSenha,
        @NotBlank(message = "Confirme a nova senha.") @Size(max = 200, message = "Máximo de 200 caracteres.") String confirmacao) {

    @Override
    public String toString() {
        return "TrocarSenhaRequest[...]";
    }
}
