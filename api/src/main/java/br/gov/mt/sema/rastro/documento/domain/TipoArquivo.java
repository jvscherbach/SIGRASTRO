package br.gov.mt.sema.rastro.documento.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Tipo do arquivo pela extensão do nome (os metadados reais só são conhecidos ao obter o arquivo). Sem extensão ou
 * com extensão não reconhecida o tipo é {@link #DESCONHECIDO}: o documento aparece e é conferido ao gerar o dossiê.
 */
public enum TipoArquivo {

    PDF(Set.of("pdf")),
    PLANILHA(Set.of("xls", "xlsx", "ods")),
    TEXTO(Set.of("doc", "docx", "odt", "rtf", "html", "txt")),
    IMAGEM(Set.of("png", "jpg", "jpeg")),
    GEOMETRIA(Set.of("shp", "kml", "kmz")),
    COMPACTADO(Set.of("zip", "rar", "7z")),
    DESCONHECIDO(Set.of());

    private final Set<String> extensoes;

    TipoArquivo(Set<String> extensoes) {
        this.extensoes = extensoes;
    }

    public static TipoArquivo daExtensao(String extensao) {
        if (extensao == null) {
            return DESCONHECIDO;
        }
        for (TipoArquivo tipo : values()) {
            if (tipo.extensoes.contains(extensao)) {
                return tipo;
            }
        }
        return DESCONHECIDO;
    }

    /**
     * Extensão que a conversão para PDF atende (RF13): planilhas, textos e imagens. {@code txt} é do tipo
     * {@link #TEXTO}, mas não está entre os formatos convertíveis.
     */
    public static boolean convertivel(String extensao) {
        TipoArquivo tipo = daExtensao(extensao);
        return (tipo == PLANILHA || tipo == TEXTO || tipo == IMAGEM) && !"txt".equals(extensao);
    }

    /** Extensão em minúsculas, sem o ponto; vazia quando o nome não tem extensão. */
    /**
     * Nome do arquivo sem a extensão ({@code Teste de bombeamento.xlsx} vira {@code Teste de bombeamento}). Só é
     * extensão o que vem depois do último ponto quando tem de 1 a 5 letras ou dígitos (ponto no meio, como em
     * {@code Relatório v1.2 final}, não é extensão). Sem extensão devolve o próprio nome; nome só com extensão
     * ({@code .xlsx}) ou vazio devolve vazio.
     */
    public static String semExtensao(String nomeArquivo) {
        if (nomeArquivo == null) {
            return "";
        }
        String nome = nomeArquivo.trim();
        int ponto = nome.lastIndexOf('.');
        if (ponto < 0) {
            return nome;
        }
        String sufixo = nome.substring(ponto + 1);
        boolean extensao = !sufixo.isEmpty() && sufixo.length() <= 5 && sufixo.chars().allMatch(Character::isLetterOrDigit);
        if (extensao) {
            return nome.substring(0, ponto).trim();
        }
        return sufixo.isEmpty() ? nome.substring(0, ponto).trim() : nome;
    }

    public static String extensaoDe(String nomeArquivo) {
        if (nomeArquivo == null) {
            return "";
        }
        String nome = nomeArquivo.trim();
        int ponto = nome.lastIndexOf('.');
        if (ponto < 0 || ponto == nome.length() - 1) {
            return "";
        }
        return nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }
}
