package br.gov.mt.sema.rastro.caixa.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;

import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Chave opaca de um responsável (S11): hash com sal (HMAC-SHA256, 16 caracteres hexadecimais) do identificador dele no
 * Camunda. É o que o instantâneo da caixa e do painel guardam no lugar do CPF; o sal é do processo (o bloco 5 o sorteia a
 * cada subida), então a chave não serve fora desta execução e não se reverte ao CPF. Nunca é calculada no adaptador do SIGA:
 * quem chama entrega a chave pronta em {@link AnalistaParaLeitura}.
 */
public record ChaveDoResponsavel(String valor) {

    private static final String ALGORITMO = "HmacSHA256";
    private static final int CARACTERES = 16;

    public ChaveDoResponsavel {
        Objects.requireNonNull(valor);
        if (valor.isBlank()) {
            throw new IllegalArgumentException("chave do responsável vazia");
        }
    }

    /** Calcula a chave de um identificador com o sal do processo. */
    public static ChaveDoResponsavel de(IdentificadorDoResponsavel identificador, byte[] sal) {
        Objects.requireNonNull(identificador);
        if (sal == null || sal.length < 16) {
            throw new IllegalArgumentException("sal curto demais");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(sal, ALGORITMO));
            byte[] hash = mac.doFinal(identificador.valorProtegido().getBytes(StandardCharsets.UTF_8));
            return new ChaveDoResponsavel(HexFormat.of().formatHex(hash).substring(0, CARACTERES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("não foi possível calcular a chave do responsável");
        }
    }

    @Override
    public String toString() {
        return "ChaveDoResponsavel[" + valor + "]";
    }
}
