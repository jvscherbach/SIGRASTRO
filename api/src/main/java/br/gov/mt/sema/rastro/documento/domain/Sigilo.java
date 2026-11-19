package br.gov.mt.sema.rastro.documento.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * RN06: normalização tolerante do sigilo (as colunas SIGILOSO e SIGILO são NUMBER, 0 ou 1; P05 confirma os valores
 * no banco). Número diferente de zero e os textos S, SIM, Y, YES, T, TRUE, 1 são sigilosos; nulo, zero e
 * N, NAO, NÃO, NO, F, FALSE, 0 não são. Qualquer outro valor não nulo é sigiloso ("na dúvida").
 */
public final class Sigilo {

    private static final Set<String> VERDADEIROS = Set.of("S", "SIM", "Y", "YES", "T", "TRUE", "1");
    private static final Set<String> FALSOS = Set.of("N", "NAO", "NÃO", "NO", "F", "FALSE", "0");

    private Sigilo() {
    }

    public static boolean ehSigiloso(Object valor) {
        if (valor == null) {
            return false;
        }
        if (valor instanceof Boolean booleano) {
            return booleano;
        }
        if (valor instanceof BigDecimal decimal) {
            return decimal.signum() != 0;
        }
        if (valor instanceof Number numero) {
            return numero.doubleValue() != 0;
        }
        String texto = valor.toString().trim().toUpperCase(Locale.ROOT);
        if (texto.isEmpty() || FALSOS.contains(texto)) {
            return false;
        }
        return true;
    }
}
