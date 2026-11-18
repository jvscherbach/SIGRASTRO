package br.gov.mt.sema.vistas.acesso.domain;

/** Validação pelos dígitos verificadores e máscara do CPF/CNPJ de um usuário. Trabalha sempre com dígitos. */
public final class CpfCnpjDoUsuario {

    private static final int TAMANHO_CPF = 11;
    private static final int TAMANHO_CNPJ = 14;
    private static final int[] PESOS_CNPJ = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CpfCnpjDoUsuario() {
    }

    public static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    public static boolean valido(String digitos) {
        if (digitos == null || digitos.chars().distinct().count() == 1) {
            return false;
        }
        return switch (digitos.length()) {
            case TAMANHO_CPF -> digitoDoCpf(digitos, 9) == digitos.charAt(9) - '0'
                    && digitoDoCpf(digitos, 10) == digitos.charAt(10) - '0';
            case TAMANHO_CNPJ -> digitoDoCnpj(digitos, 12) == digitos.charAt(12) - '0'
                    && digitoDoCnpj(digitos, 13) == digitos.charAt(13) - '0';
            default -> false;
        };
    }

    /** {@code ***.456.789-**} ou {@code **.345.678/****-**}; sem cadastro, {@code null}. Nunca devolve o número inteiro. */
    public static String mascarar(String valor) {
        String d = somenteDigitos(valor);
        if (d.length() == TAMANHO_CPF) {
            return "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**";
        }
        if (d.length() == TAMANHO_CNPJ) {
            return "**." + d.substring(2, 5) + "." + d.substring(5, 8) + "/****-**";
        }
        return null;
    }

    private static int digitoDoCpf(String digitos, int posicao) {
        int soma = 0;
        for (int i = 0; i < posicao; i++) {
            soma += (digitos.charAt(i) - '0') * (posicao + 1 - i);
        }
        return resto(soma);
    }

    private static int digitoDoCnpj(String digitos, int posicao) {
        int soma = 0;
        int inicioDosPesos = PESOS_CNPJ.length - posicao;
        for (int i = 0; i < posicao; i++) {
            soma += (digitos.charAt(i) - '0') * PESOS_CNPJ[inicioDosPesos + i];
        }
        return resto(soma);
    }

    private static int resto(int soma) {
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
