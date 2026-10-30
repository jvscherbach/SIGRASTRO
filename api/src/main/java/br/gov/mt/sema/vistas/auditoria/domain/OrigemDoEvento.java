package br.gov.mt.sema.vistas.auditoria.domain;

import java.util.regex.Pattern;

/**
 * IP de origem da requisição. O nginx do front acrescenta o endereço real ao final do {@code X-Forwarded-For}
 * ({@code $proxy_add_x_forwarded_for}); o que vem antes foi escrito pelo cliente e não é confiável, por isso vale o
 * último endereço válido. Sem cabeçalho, vale o remoto. O que não parece IP é descartado.
 */
public final class OrigemDoEvento {

    private static final Pattern IP = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}|[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");
    private static final int MAX_IP = 45;

    private OrigemDoEvento() {
    }

    public static String de(String xForwardedFor, String enderecoRemoto) {
        if (xForwardedFor != null) {
            String[] enderecos = xForwardedFor.split(",");
            for (int i = enderecos.length - 1; i >= 0; i--) {
                String endereco = validar(enderecos[i]);
                if (endereco != null) {
                    return endereco;
                }
            }
        }
        return validar(enderecoRemoto);
    }

    /** O endereço aparado, ou {@code null} se não parecer um IP. */
    static String validar(String endereco) {
        if (endereco == null) {
            return null;
        }
        String aparado = endereco.strip();
        return aparado.length() <= MAX_IP && IP.matcher(aparado).matches() ? aparado : null;
    }
}
