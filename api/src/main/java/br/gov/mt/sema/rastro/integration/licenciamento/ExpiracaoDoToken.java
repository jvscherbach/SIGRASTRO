package br.gov.mt.sema.rastro.integration.licenciamento;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lê só o campo {@code exp} de um token JWT, sem validar a assinatura: é uma informação para a tela, não uma decisão de
 * acesso. Token que não é JWT, ou sem {@code exp}, dá vazio. Nada do token vai para log ou erro.
 */
final class ExpiracaoDoToken {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ExpiracaoDoToken() {
    }

    static Optional<Instant> de(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] partes = token.split("\\.");
        if (partes.length < 2) {
            return Optional.empty();
        }
        try {
            JsonNode exp = JSON.readTree(Base64.getUrlDecoder().decode(partes[1])).get("exp");
            return exp != null && exp.isNumber() ? Optional.of(Instant.ofEpochSecond(exp.asLong())) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
