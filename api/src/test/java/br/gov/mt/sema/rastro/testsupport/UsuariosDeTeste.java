package br.gov.mt.sema.rastro.testsupport;

/**
 * Logins e senhas de teste dos usuários padrão (L1, bloco 5). Os valores de senha são os mesmos de
 * {@code src/test/resources/application.properties}; o Gestor de teste é criado por {@link GestorDeTeste}. Valores
 * óbvios de teste, só para código de teste.
 */
public final class UsuariosDeTeste {

    public static final String OPERADOR = "operador";
    public static final String ANALISTA = "analista";
    public static final String GESTOR = "gestor";
    public static final String ADMIN = "admin";

    public static final String SENHA_OPERADOR = "senha-de-teste-op-01";
    public static final String SENHA_ANALISTA = "senha-de-teste-an-02";
    public static final String SENHA_GESTOR = "senha-de-teste-ge-04";
    public static final String SENHA_ADMIN = "senha-de-teste-ad-03";

    /** CNPJ (válido pelos dígitos verificadores, fictício) do interessado vinculado ao Operador de teste. */
    public static final String CNPJ_DO_OPERADOR = "11.222.333/0001-81";

    private UsuariosDeTeste() {
    }

    /** Senha de teste do login informado (usuário desconhecido recebe uma senha qualquer, que não vale). */
    public static String senhaDe(String login) {
        return switch (login == null ? "" : login) {
            case OPERADOR -> SENHA_OPERADOR;
            case ANALISTA -> SENHA_ANALISTA;
            case GESTOR -> SENHA_GESTOR;
            case ADMIN -> SENHA_ADMIN;
            default -> "senha-de-teste-qualquer-99";
        };
    }
}
