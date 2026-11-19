package br.gov.mt.sema.rastro.integration.libreoffice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Limpeza do HTML antes da conversão: nada que gere requisição sobra; o conteúdo e a formatação ficam. */
class HtmlSemRecursosExternosTest {

    private static final String REMOTO = "http://127.0.0.1:1/";

    @Test
    void removeTudoQueBuscariaRecursoRemoto() {
        String limpo = HtmlSemRecursosExternos.limpar("""
                <html><head>
                <link rel="stylesheet" href="%1$sestilo.css">
                <base href="%1$s">
                <meta http-equiv="refresh" content="0;url=%1$s">
                <style>body { background: url(%1$sfundo.png) }</style>
                <script src="%1$sx.js"></script>
                </head><body background="%1$sfundo2.png">
                <p>Associação Residencial Vale Verde</p>
                <img src="%1$simagem.png"><img src="relativa.png"><img srcset="%1$sa.png 2x">
                <iframe src="%1$squadro"></iframe><object data="%1$sobj"></object><embed src="%1$semb">
                <video src="%1$sv.mp4"></video><audio src="%1$sa.mp3"></audio>
                <form action="%1$senviar"><input value="x"></form>
                <div style="background-image: url('%1$sdiv.png')">div</div>
                <div style="background: u\\72l(%1$sescapado.png)">escapado</div>
                <svg><image href="%1$ssvg.png"/></svg>
                </body></html>
                """.formatted(REMOTO));

        assertThat(limpo).contains("Associação Residencial Vale Verde").contains("div").contains("escapado");
        assertThat(limpo).doesNotContain("127.0.0.1").doesNotContain("relativa.png")
                .doesNotContainIgnoringCase("<script").doesNotContainIgnoringCase("<iframe")
                .doesNotContainIgnoringCase("<link").doesNotContainIgnoringCase("<base")
                .doesNotContainIgnoringCase("http-equiv").doesNotContainIgnoringCase("<form");
    }

    @Test
    void mantemTabelasEstilosSegurosEImagemEmbutida() {
        String imagem = "data:image/png;base64,iVBORw0KGgo=";
        String limpo = HtmlSemRecursosExternos.limpar("""
                <html><head><style>td { border: 1px solid black; }</style></head><body>
                <h1>Título</h1>
                <table border="1"><tr><td colspan="2" style="color: red">célula</td></tr></table>
                <img src="%s">
                <a href="https://exemplo.invalid/">link</a>
                </body></html>
                """.formatted(imagem));

        assertThat(limpo).contains("<h1>Título</h1>").contains("colspan=\"2\"").contains("color: red")
                .contains("td { border: 1px solid black; }").contains(imagem)
                .contains("<meta charset=\"utf-8\">");
    }
}
