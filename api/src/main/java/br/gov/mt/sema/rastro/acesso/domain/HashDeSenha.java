package br.gov.mt.sema.rastro.acesso.domain;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash de senha com {@code PBKDF2WithHmacSHA256} do JDK, sal aleatório por senha e formato autodescritivo
 * ({@code pbkdf2-sha256$iteracoes$sal$hash}, sal e hash em Base64): o custo pode mudar depois e os hashes antigos
 * continuam verificando com o custo com que foram gerados. A comparação é em tempo constante.
 *
 * <p>{@link #hashFalso()} existe para o login de usuário inexistente ou desativado fazer o mesmo trabalho de uma senha
 * verdadeira, sem revelar pelo tempo de resposta se o login existe. O hash falso nasce de um segredo aleatório
 * descartado; nada dele fica no código.
 */
public class HashDeSenha {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String ROTULO = "pbkdf2-sha256";
    private static final int BYTES_DO_SAL = 16;
    private static final int BITS_DO_HASH = 256;
    private static final int MAX_ITERACOES_LIDAS = 10_000_000;

    private final int iteracoes;
    private final SecureRandom aleatorio = new SecureRandom();
    private final String falso;

    public HashDeSenha(int iteracoes) {
        if (iteracoes < 1) {
            throw new IllegalArgumentException("Iterações inválidas");
        }
        this.iteracoes = iteracoes;
        byte[] segredo = new byte[32];
        aleatorio.nextBytes(segredo);
        this.falso = gerar(Base64.getEncoder().encodeToString(segredo));
    }

    public int iteracoes() {
        return iteracoes;
    }

    /** Hash novo (sal novo a cada chamada) com o custo atual. */
    public String gerar(String senha) {
        byte[] sal = new byte[BYTES_DO_SAL];
        aleatorio.nextBytes(sal);
        byte[] derivado = derivar(senha, sal, iteracoes);
        return ROTULO + "$" + iteracoes + "$" + Base64.getEncoder().encodeToString(sal) + "$"
                + Base64.getEncoder().encodeToString(derivado);
    }

    /**
     * Confere a senha contra um hash guardado. Hash ausente ou malformado nunca confere, mas faz o mesmo trabalho de uma
     * conferência verdadeira.
     */
    public boolean confere(String senha, String guardado) {
        String[] partes = guardado == null ? new String[0] : guardado.split("\\$", -1);
        boolean formatoValido = partes.length == 4 && ROTULO.equals(partes[0]);
        int custo = iteracoes;
        byte[] sal = null;
        byte[] esperado = null;
        if (formatoValido) {
            try {
                custo = Integer.parseInt(partes[1]);
                sal = Base64.getDecoder().decode(partes[2]);
                esperado = Base64.getDecoder().decode(partes[3]);
                formatoValido = custo >= 1 && custo <= MAX_ITERACOES_LIDAS && sal.length > 0 && esperado.length > 0;
            } catch (IllegalArgumentException e) {
                formatoValido = false;
            }
        }
        if (!formatoValido) {
            custo = iteracoes;
            sal = new byte[BYTES_DO_SAL];
            esperado = new byte[BITS_DO_HASH / 8];
        }
        byte[] obtido = derivar(senha, sal, custo, esperado.length * 8);
        boolean igual = MessageDigest.isEqual(esperado, obtido);
        return formatoValido && igual;
    }

    /** Hash que nunca confere com senha alguma, com o custo atual. */
    public String hashFalso() {
        return falso;
    }

    private static byte[] derivar(String senha, byte[] sal, int custo) {
        return derivar(senha, sal, custo, BITS_DO_HASH);
    }

    private static byte[] derivar(String senha, byte[] sal, int custo, int bits) {
        char[] caracteres = (senha == null ? "" : senha).toCharArray();
        PBEKeySpec especificacao = new PBEKeySpec(caracteres, sal, custo, bits);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(especificacao).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Algoritmo de hash de senha indisponível", e);
        } finally {
            especificacao.clearPassword();
            Arrays.fill(caracteres, '\0');
        }
    }
}
