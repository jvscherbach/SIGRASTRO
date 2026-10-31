package br.gov.mt.sema.vistas.auditoria.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Limpeza de todo texto que entra num evento (RNF09): tira caracteres de controle, apara, limita o tamanho e troca por
 * {@value #REMOVIDO} o que parece token, cookie, URL, caminho, chave opaca ou CPF/CNPJ completo. Os módulos já não
 * mandam esses dados; aqui fica a segunda barreira, para o texto livre (motivo de falha, título vindo do SIGA) que
 * possa trazê-los. Nunca lança.
 */
public final class TextoDoEvento {

    public static final String REMOVIDO = "[removido]";

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern QUEBRAS = Pattern.compile("[\\r\\n\\t\\p{Zl}\\p{Zp}]+");
    private static final Pattern CONTROLE = Pattern.compile("[\\p{Cc}\\p{Cf}]");

    private static final List<Pattern> SENSIVEIS = List.of(
            Pattern.compile("\\b(?:set-)?cookie\\s*[=:].*$", FLAGS),
            Pattern.compile("\\b(?:authorization\\s*[=:]\\s*)?bearer\\s+\\S+", FLAGS),
            Pattern.compile("\\b(?:authorization|token|senha|password|segredo|secret|chave)\\s*[=:]\\s*\\S+", FLAGS),
            Pattern.compile("\\beyJ[\\w-]+\\.[\\w-]+\\.[\\w-]*", 0),
            Pattern.compile("\\bjdbc:\\S+", FLAGS),
            Pattern.compile("\\b[a-z][a-z0-9+.-]*://\\S+", FLAGS),
            Pattern.compile("\\b[a-z]:[\\\\/][^\\s;,]*", FLAGS),
            Pattern.compile("\\\\\\\\\\S+", 0),
            Pattern.compile("(?<![\\w.:/])/(?:[\\w.-]+/)+[\\w.-]*", FLAGS),
            Pattern.compile("(?<!\\d)\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}(?!\\d)", 0),
            Pattern.compile("(?<!\\d)\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}(?!\\d)", 0),
            Pattern.compile("(?<!\\d)(?:\\d{14}|\\d{11})(?!\\d)", 0),
            Pattern.compile("\\b[0-9a-f-]{32,}\\b", FLAGS),
            Pattern.compile("\\b[a-z0-9+=]{32,}\\b", FLAGS));

    private TextoDoEvento() {
    }

    /** O texto limpo, ou {@code null} se nada sobrar. */
    public static String limpar(String texto, int tamanhoMaximo) {
        if (texto == null) {
            return null;
        }
        String limpo = CONTROLE.matcher(QUEBRAS.matcher(texto).replaceAll(" ")).replaceAll("");
        for (Pattern sensivel : SENSIVEIS) {
            limpo = sensivel.matcher(limpo).replaceAll(REMOVIDO);
        }
        return limitar(limpo.strip(), tamanhoMaximo);
    }

    /** Só o nome, sem as pastas que vierem antes dele. */
    public static String nomeDeArquivo(String nome, int tamanhoMaximo) {
        if (nome == null) {
            return null;
        }
        return limpar(nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1), tamanhoMaximo);
    }

    /** Identificador técnico (id de pedido, de documento, correlação): só caracteres de identificador, sem troca alguma. */
    public static String identificador(String valor, int tamanhoMaximo) {
        return valor == null ? null : limitar(valor.replaceAll("[^A-Za-z0-9._:-]", ""), tamanhoMaximo);
    }

    private static String limitar(String texto, int tamanhoMaximo) {
        if (texto.isEmpty()) {
            return null;
        }
        if (texto.length() <= tamanhoMaximo) {
            return texto;
        }
        int fim = Character.isHighSurrogate(texto.charAt(tamanhoMaximo - 1)) ? tamanhoMaximo - 1 : tamanhoMaximo;
        return texto.substring(0, fim).stripTrailing();
    }
}
