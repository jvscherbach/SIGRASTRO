package br.gov.mt.sema.vistas.documento.domain;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Nomes das entradas do ZIP das geometrias (RN10, S4). O nome vem do SIGA e não é confiável: sai sem caminho, sem
 * caractere de controle ou de formatação (inclusive inversão de direção), sem os reservados do Windows e sem
 * {@code ..}; com no máximo {@value #MAXIMO} caracteres, preservando a extensão. Cada nome é único no ZIP (sem
 * diferenciar maiúsculas): na colisão, ganha o sufixo {@code -2}, {@code -3}...
 */
public final class NomesNoZip {

    static final int MAXIMO = 120;
    private static final String SEM_NOME = "geometria";

    private final Set<String> usados = new HashSet<>();

    /** Reserva e devolve o nome seguro e único para o arquivo. */
    public String reservar(String nomeDoArquivo, String extensao) {
        String nome = seguro(nomeDoArquivo, extensao);
        String base = baseDe(nome);
        String sufixo = nome.substring(base.length());
        String candidato = nome;
        for (int n = 2; !usados.add(candidato.toLowerCase(Locale.ROOT)); n++) {
            String numero = "-" + n;
            candidato = cortar(base, MAXIMO - sufixo.length() - numero.length()) + numero + sufixo;
        }
        return candidato;
    }

    static String seguro(String nomeDoArquivo, String extensao) {
        String nome = nomeDoArquivo == null ? "" : Normalizer.normalize(nomeDoArquivo, Normalizer.Form.NFC);
        // Só o último segmento: nenhum caminho entra no ZIP.
        int barra = Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\'));
        nome = nome.substring(barra + 1);
        nome = nome.replaceAll("[\\p{Cc}\\p{Cf}]", "")
                .replaceAll("[<>:\"|?*]", "_")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("\\s+", " ")
                .strip()
                .replaceAll("^[.\\s]+|[.\\s]+$", "");
        String ext = extensao == null ? "" : extensao.replaceAll("[^a-z0-9]", "");
        String sufixo = ext.isEmpty() ? "" : "." + ext;
        String base = nome;
        if (!sufixo.isEmpty() && base.toLowerCase(Locale.ROOT).endsWith(sufixo)) {
            base = base.substring(0, base.length() - sufixo.length());
        }
        base = base.replaceAll("[.\\s]+$", "");
        if (base.isEmpty()) {
            base = SEM_NOME;
        }
        return cortar(base, MAXIMO - sufixo.length()) + sufixo;
    }

    private static String baseDe(String nome) {
        int ponto = nome.lastIndexOf('.');
        return ponto <= 0 ? nome : nome.substring(0, ponto);
    }

    /** Corta sem partir um par substituto (caracteres fora do plano básico). */
    private static String cortar(String texto, int maximo) {
        if (texto.length() <= maximo) {
            return texto;
        }
        int fim = Character.isHighSurrogate(texto.charAt(maximo - 1)) ? maximo - 1 : maximo;
        return texto.substring(0, fim).replaceAll("[.\\s]+$", "");
    }
}
