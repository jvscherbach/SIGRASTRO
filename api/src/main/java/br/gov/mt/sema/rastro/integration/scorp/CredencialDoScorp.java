package br.gov.mt.sema.rastro.integration.scorp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Token do SCORP (S4). Autenticação **NÃO CONFIRMADA** (P06): o mesmo esquema da API de arquivos. Com arquivo
 * ({@code SCORP_API_TOKEN_FILE} ou, sem ele, {@code LICENCIAMENTO_API_TOKEN_FILE}) o token é relido a cada chamada e
 * tem precedência sobre o token fixo ({@code LICENCIAMENTO_API_TOKEN}). Arquivo ausente, ilegível ou vazio: a chamada
 * falha sem ir ao SCORP. O valor e o caminho nunca vão para log, erro ou resposta.
 */
final class CredencialDoScorp {

    private final String fixo;
    private final Path arquivo;

    private CredencialDoScorp(String fixo, Path arquivo) {
        this.fixo = fixo;
        this.arquivo = arquivo;
    }

    static CredencialDoScorp de(String tokenFixo, String arquivoDoScorp, String arquivoDoLicenciamento) {
        String caminho = !vazio(arquivoDoScorp) ? arquivoDoScorp : arquivoDoLicenciamento;
        if (!vazio(caminho)) {
            return new CredencialDoScorp(null, Path.of(caminho.trim()));
        }
        return new CredencialDoScorp(tokenFixo == null ? "" : tokenFixo.trim(), null);
    }

    /** O token aparado; vazio só sem arquivo e sem token fixo (a chamada vai sem autenticação). */
    String obter() {
        if (arquivo == null) {
            return fixo;
        }
        String valor;
        try {
            valor = Files.readString(arquivo, StandardCharsets.UTF_8).trim();
        } catch (IOException | RuntimeException e) {
            throw new FalhaDoScorp("credencial do SCORP não configurada");
        }
        if (valor.isEmpty()) {
            throw new FalhaDoScorp("credencial do SCORP não configurada");
        }
        return valor;
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    @Override
    public String toString() {
        return "CredencialDoScorp[" + (arquivo == null ? "fixa" : "arquivo") + "]";
    }
}
