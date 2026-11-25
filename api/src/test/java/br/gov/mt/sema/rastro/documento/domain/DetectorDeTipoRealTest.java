package br.gov.mt.sema.rastro.documento.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Detecção do tipo real pelos primeiros bytes. Todos os arquivos são sintéticos, gerados aqui. */
class DetectorDeTipoRealTest {

    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    @TempDir
    Path pasta;

    private TipoDetectado detectar(byte[] conteudo, String extensao) throws IOException {
        Path arquivo = Files.write(pasta.resolve("arquivo-de-teste"), conteudo);
        return DetectorDeTipoReal.detectar(arquivo, extensao);
    }

    private static byte[] ascii(String texto) {
        return texto.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] comResto(byte[] assinatura) {
        byte[] dados = Arrays.copyOf(assinatura, assinatura.length + 32);
        Arrays.fill(dados, assinatura.length, dados.length, (byte) 'x');
        return dados;
    }

    private static byte[] zip(Map<String, String> entradas) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            for (Map.Entry<String, String> entrada : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(entrada.getKey()));
                zip.write(entrada.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return saida.toByteArray();
    }

    @Test
    void pdfPelaAssinatura() throws IOException {
        assertThat(detectar(ascii("%PDF-1.7\n%%EOF"), "pdf")).isEqualTo(new TipoDetectado(TipoReal.PDF, "pdf"));
    }

    @Test
    void pngEJpegSaoImagem() throws IOException {
        byte[] png = comResto(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] jpeg = comResto(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

        assertThat(detectar(png, "png")).isEqualTo(new TipoDetectado(TipoReal.IMAGEM, "png"));
        assertThat(detectar(jpeg, "jpg")).isEqualTo(new TipoDetectado(TipoReal.IMAGEM, "jpg"));
    }

    @Test
    void rtfEHtmlSaoTexto() throws IOException {
        assertThat(detectar(ascii("{\\rtf1\\ansi texto}"), "rtf")).isEqualTo(new TipoDetectado(TipoReal.TEXTO, "rtf"));
        assertThat(detectar(ascii("<!DOCTYPE html><html><body>x</body></html>"), "html"))
                .isEqualTo(new TipoDetectado(TipoReal.TEXTO, "html"));
        assertThat(detectar(ascii("  \n <HTML><head></head></HTML>"), null))
                .isEqualTo(new TipoDetectado(TipoReal.TEXTO, "html"));
    }

    @Test
    void htmlComBomUtf8EReconhecido() throws IOException {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(bom);
        saida.write(ascii("<!doctype html><title>x</title>"));

        assertThat(detectar(saida.toByteArray(), "html").tipo()).isEqualTo(TipoReal.TEXTO);
    }

    @Test
    void ole2UsaAExtensaoParaEscolherEntrePlanilhaETexto() throws IOException {
        byte[] ole2 = comResto(OLE2);

        assertThat(detectar(ole2, "xls")).isEqualTo(new TipoDetectado(TipoReal.PLANILHA, "xls"));
        assertThat(detectar(ole2, "doc")).isEqualTo(new TipoDetectado(TipoReal.TEXTO, "doc"));
        // OOXML criptografado também é OLE2: a conversão ainda recebe a extensão da família e acusa a senha.
        assertThat(detectar(ole2, "XLSX")).isEqualTo(new TipoDetectado(TipoReal.PLANILHA, "xlsx"));
        assertThat(detectar(ole2, "docx").tipo()).isEqualTo(TipoReal.TEXTO);
        assertThat(detectar(ole2, "pdf").tipo()).isEqualTo(TipoReal.DESCONHECIDO);
        assertThat(detectar(ole2, null).tipo()).isEqualTo(TipoReal.DESCONHECIDO);
    }

    @Test
    void ooxmlEDistinguidoPelasEntradas() throws IOException {
        Map<String, String> planilha = new LinkedHashMap<>();
        planilha.put("[Content_Types].xml", "<Types/>");
        planilha.put("xl/workbook.xml", "<workbook/>");
        Map<String, String> texto = new LinkedHashMap<>();
        texto.put("[Content_Types].xml", "<Types/>");
        texto.put("word/document.xml", "<document/>");

        assertThat(detectar(zip(planilha), "xlsx")).isEqualTo(new TipoDetectado(TipoReal.PLANILHA, "xlsx"));
        assertThat(detectar(zip(texto), "docx")).isEqualTo(new TipoDetectado(TipoReal.TEXTO, "docx"));
    }

    @Test
    void odfEDistinguidoPeloMimetype() throws IOException {
        Map<String, String> ods = new LinkedHashMap<>();
        ods.put("mimetype", "application/vnd.oasis.opendocument.spreadsheet");
        ods.put("content.xml", "<c/>");
        Map<String, String> odt = new LinkedHashMap<>();
        odt.put("mimetype", "application/vnd.oasis.opendocument.text");
        Map<String, String> odp = new LinkedHashMap<>();
        odp.put("mimetype", "application/vnd.oasis.opendocument.presentation");

        assertThat(detectar(zip(ods), "ods")).isEqualTo(new TipoDetectado(TipoReal.PLANILHA, "ods"));
        assertThat(detectar(zip(odt), "odt")).isEqualTo(new TipoDetectado(TipoReal.TEXTO, "odt"));
        assertThat(detectar(zip(odp), "odp").tipo()).isEqualTo(TipoReal.DESCONHECIDO);
    }

    @Test
    void zipGenericoEhCompactado() throws IOException {
        Map<String, String> entradas = new LinkedHashMap<>();
        entradas.put("foto.png", "x");
        entradas.put("notas.txt", "y");

        assertThat(detectar(zip(entradas), "zip")).isEqualTo(new TipoDetectado(TipoReal.COMPACTADO, "zip"));
        assertThat(detectar(zip(entradas), "docx").tipo()).isEqualTo(TipoReal.COMPACTADO);
    }

    @Test
    void zipComPlanilhaETextoJuntosNaoEAfirmado() throws IOException {
        Map<String, String> entradas = new LinkedHashMap<>();
        entradas.put("xl/workbook.xml", "<w/>");
        entradas.put("word/document.xml", "<d/>");

        assertThat(detectar(zip(entradas), "xlsx").tipo()).isEqualTo(TipoReal.DESCONHECIDO);
    }

    @Test
    void arquivoVazioETruncadoSaoDesconhecidos() throws IOException {
        assertThat(detectar(new byte[0], "pdf").tipo()).isEqualTo(TipoReal.DESCONHECIDO);
        assertThat(detectar(new byte[] {'%', 'P', 'D'}, "pdf").tipo()).isEqualTo(TipoReal.DESCONHECIDO);
        assertThat(detectar(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11}, "xls").tipo()).isEqualTo(TipoReal.DESCONHECIDO);

        byte[] inteiro = zip(Map.of("xl/workbook.xml", "<w/>"));
        byte[] cortado = Arrays.copyOf(inteiro, inteiro.length / 2);
        assertThat(detectar(cortado, "xlsx").tipo()).isEqualTo(TipoReal.DESCONHECIDO);
    }

    @Test
    void extensaoEnganosaNaoPrevaleceSobreOConteudo() throws IOException {
        Map<String, String> planilha = new LinkedHashMap<>();
        planilha.put("xl/workbook.xml", "<w/>");

        // .pdf com conteúdo de planilha é planilha; .xlsx com conteúdo de PDF é PDF.
        assertThat(detectar(zip(planilha), "pdf")).isEqualTo(new TipoDetectado(TipoReal.PLANILHA, "xlsx"));
        assertThat(detectar(ascii("%PDF-1.4 resto"), "xlsx").tipo()).isEqualTo(TipoReal.PDF);
    }

    @Test
    void textoQualquerEBinarioQualquerSaoDesconhecidos() throws IOException {
        assertThat(detectar(ascii("apenas um texto simples"), "txt").tipo()).isEqualTo(TipoReal.DESCONHECIDO);
        assertThat(detectar(new byte[] {0, 1, 2, 3, '<', 'h', 't', 'm', 'l', '>'}, null).tipo())
                .isEqualTo(TipoReal.DESCONHECIDO);
    }

    @Test
    void naoLeOArquivoInteiro() throws IOException {
        Path grande = pasta.resolve("grande.pdf");
        try (OutputStream saida = Files.newOutputStream(grande)) {
            saida.write(ascii("%PDF-1.7\n"));
            saida.write(new byte[8 * 1024 * 1024]);
        }

        assertThat(DetectorDeTipoReal.detectar(grande, "pdf").tipo()).isEqualTo(TipoReal.PDF);
        assertThat(DetectorDeTipoReal.TAMANHO_DO_CABECALHO).isLessThanOrEqualTo(8192);
    }

    @Test
    void arquivoInexistenteEDesconhecido() {
        assertThat(DetectorDeTipoReal.detectar(pasta.resolve("nao-existe"), "pdf").tipo())
                .isEqualTo(TipoReal.DESCONHECIDO);
    }

    // ---- V6: Office antigo sem extensão utilizável, pela estrutura (nomes dos fluxos) ----

    private static byte[] xlsSintetico() throws IOException {
        try (org.apache.poi.hssf.usermodel.HSSFWorkbook livro = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
                ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            livro.createSheet("Dados").createRow(0).createCell(0).setCellValue("valor fictício");
            livro.write(saida);
            return saida.toByteArray();
        }
    }

    private static byte[] docSintetico() throws IOException {
        try (org.apache.poi.poifs.filesystem.POIFSFileSystem fs = new org.apache.poi.poifs.filesystem.POIFSFileSystem();
                ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            fs.createDocument(new java.io.ByteArrayInputStream(new byte[1024]), "WordDocument");
            fs.writeFilesystem(saida);
            return saida.toByteArray();
        }
    }

    @Test
    void ole2SemExtensaoEIdentificadoPelosFluxosDoDiretorio() throws IOException {
        byte[] xls = xlsSintetico();
        byte[] doc = docSintetico();

        for (String extensao : new String[] {null, "", "1", "tmp"}) {
            assertThat(detectar(xls, extensao)).isEqualTo(new TipoDetectado(TipoReal.PLANILHA, "xls"));
            assertThat(detectar(doc, extensao)).isEqualTo(new TipoDetectado(TipoReal.TEXTO, "doc"));
        }
    }

    @Test
    void ole2ComExtensaoDeclaradaDePdfNaoERevisto() throws IOException {
        assertThat(detectar(xlsSintetico(), "pdf").tipo()).isEqualTo(TipoReal.DESCONHECIDO);
    }

    @Test
    void ole2ComExtensaoDeOfficeContinuaPelaExtensao() throws IOException {
        assertThat(detectar(xlsSintetico(), "doc")).isEqualTo(new TipoDetectado(TipoReal.TEXTO, "doc"));
    }

    @Test
    void ole2TruncadoOuSemFluxoConhecidoContinuaDesconhecido() throws IOException {
        byte[] xls = xlsSintetico();

        assertThat(detectar(Arrays.copyOf(xls, 600), null).tipo()).isEqualTo(TipoReal.DESCONHECIDO);
        assertThat(detectar(comResto(OLE2), null).tipo()).isEqualTo(TipoReal.DESCONHECIDO);
        // Cabeçalho com tamanho de setor absurdo.
        byte[] ruim = Arrays.copyOf(xls, xls.length);
        ruim[0x1E] = 31;
        assertThat(detectar(ruim, null).tipo()).isEqualTo(TipoReal.DESCONHECIDO);
    }
}
