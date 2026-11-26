package br.gov.mt.sema.rastro.acesso.application;

import java.util.Locale;

import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;

/**
 * Política de senha, aplicada ao definir, redefinir ou trocar a senha (nunca ao entrar): de 12 a 200 caracteres, não só
 * espaços e sem ser igual ao login nem conter o login. As mensagens não revelam mais do que isso.
 */
public final class PoliticaDeSenha {

    public static final int MINIMO = 12;
    public static final int MAXIMO = 200;
    public static final String RESUMO = "A senha deve ter de " + MINIMO + " a " + MAXIMO
            + " caracteres e não pode ser igual ao login nem conter o login.";

    private PoliticaDeSenha() {
    }

    /** @throws ParametroInvalidoException (campo {@code senha}) se a senha não atender */
    public static void validar(String login, String senha) {
        if (!atende(login, senha)) {
            throw new ParametroInvalidoException("senha", motivo(login, senha));
        }
    }

    public static boolean atende(String login, String senha) {
        return motivo(login, senha) == null;
    }

    private static String motivo(String login, String senha) {
        if (senha == null || senha.isBlank()) {
            return "Informe a senha. " + RESUMO;
        }
        if (senha.length() < MINIMO || senha.length() > MAXIMO) {
            return RESUMO;
        }
        String loginNormalizado = login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
        if (!loginNormalizado.isEmpty() && senha.toLowerCase(Locale.ROOT).contains(loginNormalizado)) {
            return RESUMO;
        }
        return null;
    }
}
