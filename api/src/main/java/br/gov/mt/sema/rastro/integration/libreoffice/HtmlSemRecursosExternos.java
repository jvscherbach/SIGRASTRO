package br.gov.mt.sema.rastro.integration.libreoffice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/**
 * Reescreve o HTML recebido antes da conversão, para que nada nele gere requisição: o LibreOffice baixaria imagens e
 * folhas de estilo remotas ao carregar. Fica só o conteúdo e a formatação: texto, títulos, listas, tabelas e estilos.
 *
 * <ul>
 *   <li>saem {@code script}, {@code iframe}, {@code object}, {@code embed}, {@code link}, {@code base}, {@code meta}
 *       e formulários (a lista permitida não os tem);</li>
 *   <li>imagem só com {@code data:} (embutida); {@code src} remoto ou relativo é removido: a imagem vira conteúdo
 *       ausente, não requisição;</li>
 *   <li>atributo {@code style} e bloco {@code <style>} com qualquer coisa que possa buscar recurso ({@code url(},
 *       {@code @import}, {@code image-set}, {@code src(}) ou esconder isso (escape com barra invertida) são
 *       descartados inteiros;</li>
 *   <li>links ({@code a href}) ficam, porque converter não os segue.</li>
 * </ul>
 */
final class HtmlSemRecursosExternos {

    /** HTML acima disto não é limpo nem convertido: o jsoup monta a árvore inteira em memória. */
    static final long TAMANHO_MAXIMO = 20L * 1024 * 1024;

    private static final Pattern CSS_ARRISCADO =
            Pattern.compile("url|@import|image-set|image\\(|src\\(|expression|behavior|\\\\|@font-face");

    private static final Safelist PERMITIDOS = Safelist.relaxed()
            .addTags("font", "center", "hr", "span", "div", "small", "big", "s", "strike", "tt", "kbd", "samp")
            .addAttributes(":all", "style", "align", "valign", "width", "height", "bgcolor", "border", "cellpadding",
                    "cellspacing", "color", "face", "size", "dir", "lang", "class", "id", "title", "nowrap")
            .removeProtocols("img", "src", "http", "https")
            .addProtocols("img", "src", "data");

    private HtmlSemRecursosExternos() {
    }

    /** Lê {@code origem}, limpa e grava em {@code destino} em UTF-8. */
    static void limpar(Path origem, Path destino) throws IOException {
        Document sujo = Jsoup.parse(origem.toFile(), null, "");
        Files.writeString(destino, limpar(sujo), StandardCharsets.UTF_8);
    }

    static String limpar(String html) {
        return limpar(Jsoup.parse(html, ""));
    }

    private static String limpar(Document sujo) {
        Document limpo = new Cleaner(PERMITIDOS).clean(sujo);
        for (Element comEstilo : limpo.select("[style]")) {
            if (arriscado(comEstilo.attr("style"))) {
                comEstilo.removeAttr("style");
            }
        }
        limpo.head().appendElement("meta").attr("charset", "utf-8");
        for (Element estilo : sujo.select("style")) {
            String css = estilo.data();
            if (!arriscado(css)) {
                limpo.head().appendElement("style").appendChild(new DataNode(css));
            }
        }
        limpo.outputSettings().charset(StandardCharsets.UTF_8).escapeMode(Entities.EscapeMode.base);
        return "<!DOCTYPE html>\n" + limpo.outerHtml();
    }

    private static boolean arriscado(String css) {
        return CSS_ARRISCADO.matcher(css.toLowerCase(Locale.ROOT)).find();
    }
}
