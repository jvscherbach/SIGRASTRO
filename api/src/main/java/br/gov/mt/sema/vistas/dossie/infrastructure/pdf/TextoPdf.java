package br.gov.mt.sema.vistas.dossie.infrastructure.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Texto para as fontes padrão do PDFBox (codificação WinAnsi): o que a fonte não codifica vira "?" (ç, ã, é, º e
 * pontuação comum cabem), e o texto longo é quebrado por largura.
 */
final class TextoPdf {

    private TextoPdf() {
    }

    static String seguro(PDFont fonte, String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder saida = new StringBuilder(texto.length());
        texto.codePoints().forEach(cp -> {
            if (Character.isWhitespace(cp) || Character.isISOControl(cp)) {
                saida.append(' ');
            } else if (cp <= 0xFFFF && codifica(fonte, (char) cp)) {
                saida.append((char) cp);
            } else {
                saida.append('?');
            }
        });
        return saida.toString().replaceAll(" {2,}", " ").trim();
    }

    private static boolean codifica(PDFont fonte, char c) {
        try {
            fonte.getStringWidth(String.valueOf(c));
            return true;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }

    static float largura(PDFont fonte, String texto, float tamanho) {
        try {
            return fonte.getStringWidth(texto) / 1000f * tamanho;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao medir texto", e);
        }
    }

    /** Quebra por palavras; palavra maior que a linha é cortada. */
    static List<String> quebrar(PDFont fonte, String texto, float tamanho, float larguraMaxima) {
        List<String> linhas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : seguro(fonte, texto).split(" ")) {
            if (palavra.isEmpty()) {
                continue;
            }
            String tentativa = atual.length() == 0 ? palavra : atual + " " + palavra;
            if (largura(fonte, tentativa, tamanho) <= larguraMaxima) {
                atual = new StringBuilder(tentativa);
                continue;
            }
            if (atual.length() > 0) {
                linhas.add(atual.toString());
            }
            atual = new StringBuilder(cortar(fonte, palavra, tamanho, larguraMaxima));
        }
        if (atual.length() > 0) {
            linhas.add(atual.toString());
        }
        return linhas;
    }

    /** Corta com reticências para caber na largura. */
    static String cortar(PDFont fonte, String texto, float tamanho, float larguraMaxima) {
        String limpo = seguro(fonte, texto);
        if (largura(fonte, limpo, tamanho) <= larguraMaxima) {
            return limpo;
        }
        String corte = limpo;
        while (corte.length() > 1 && largura(fonte, corte + "...", tamanho) > larguraMaxima) {
            corte = corte.substring(0, corte.length() - 1);
        }
        return corte.stripTrailing() + "...";
    }
}
