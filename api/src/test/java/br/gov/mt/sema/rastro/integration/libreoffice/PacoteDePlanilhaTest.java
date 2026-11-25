package br.gov.mt.sema.rastro.integration.libreoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Escolha do perfil por pacote (V6, bloco 3): só a lista de entradas e as relações; sem LibreOffice. */
class PacoteDePlanilhaTest {

    private static final String REL_INTERNA = "<Relationships><Relationship Id=\"r1\" Target=\"sheet1.xml\"/></Relationships>";
    private static final String REL_EXTERNA = "<Relationships><Relationship Id=\"r1\" Target=\"http://exemplo.invalido/x\""
            + " TargetMode=\"External\"/></Relationships>";

    @TempDir
    Path pasta;

    @Test
    void planilhaComumNaoTemConteudoAtivoNemGraficos() throws IOException {
        PacoteDePlanilha.Analise a = analisar(Map.of("xl/workbook.xml", "<x/>", "xl/_rels/workbook.xml.rels", REL_INTERNA));

        assertThat(a.conteudoAtivo()).isFalse();
        assertThat(a.graficos()).isFalse();
    }

    @Test
    void graficosEDesenhosNaoContamComoConteudoAtivo() throws IOException {
        PacoteDePlanilha.Analise a = analisar(Map.of("xl/workbook.xml", "<x/>", "xl/charts/chart1.xml", "<c/>",
                "xl/drawings/drawing1.xml", "<d/>", "xl/drawings/_rels/drawing1.xml.rels", REL_INTERNA));

        assertThat(a.conteudoAtivo()).isFalse();
        assertThat(a.graficos()).isTrue();
    }

    @Test
    void macroVbaMantemOPerfilEstrito() throws IOException {
        assertThat(analisar(Map.of("xl/vbaProject.bin", "qualquer")).conteudoAtivo()).isTrue();
    }

    @Test
    void objetoEmbutidoOleMantemOPerfilEstrito() throws IOException {
        assertThat(analisar(Map.of("xl/embeddings/oleObject1.bin", "qualquer")).conteudoAtivo()).isTrue();
        assertThat(analisar(Map.of("xl/oleObject2.bin", "qualquer")).conteudoAtivo()).isTrue();
    }

    @Test
    void vinculoExternoEActiveXMantemOPerfilEstrito() throws IOException {
        assertThat(analisar(Map.of("xl/externalLinks/externalLink1.xml", "<e/>")).conteudoAtivo()).isTrue();
        assertThat(analisar(Map.of("xl/activeX/activeX1.xml", "<a/>")).conteudoAtivo()).isTrue();
    }

    @Test
    void relacaoComDestinoExternoMantemOPerfilEstrito() throws IOException {
        assertThat(analisar(Map.of("xl/worksheets/_rels/sheet1.xml.rels", REL_EXTERNA)).conteudoAtivo()).isTrue();
    }

    @Test
    void arquivoIlegivelNaoEZipVaiParaOPerfilEstrito() throws IOException {
        Path lixo = Files.write(pasta.resolve("nao-e-zip.xlsx"), "texto".getBytes(StandardCharsets.UTF_8));

        assertThat(PacoteDePlanilha.analisar(lixo).conteudoAtivo()).isTrue();
    }

    private PacoteDePlanilha.Analise analisar(Map<String, String> entradas) throws IOException {
        Path arquivo = pasta.resolve("pacote-" + System.nanoTime() + ".xlsx");
        try (OutputStream saida = Files.newOutputStream(arquivo); ZipOutputStream zip = new ZipOutputStream(saida)) {
            for (Map.Entry<String, String> e : new LinkedHashMap<>(entradas).entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return PacoteDePlanilha.analisar(arquivo);
    }
}
