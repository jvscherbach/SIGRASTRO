package br.gov.mt.sema.vistas.integration.libreoffice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Arquivos hostis sintéticos, gerados por código (S3, bloco 8): macro, vínculo externo, recurso remoto, ZIP bomba,
 * planilhas enormes. Os endereços remotos apontam para uma armadilha local do próprio teste; nenhum dado real.
 */
final class ArquivosHostis {

    static final String VISIVEL = "Conteudo visivel do documento hostil";

    private ArquivosHostis() {
    }

    /**
     * ODT com macro Basic ligada à abertura do documento (evento {@code dom:load}): se executada, grava o
     * {@code marcador}. O conversor nunca pode executá-la.
     */
    static byte[] odtComMacroAoAbrir(Path marcador) throws IOException {
        String codigo = """
                Sub Main
                  Dim n As Integer
                  n = FreeFile
                  Open "%s" For Output As #n
                  Print #n, "macro executada"
                  Close #n
                End Sub
                """.formatted(marcador.toAbsolutePath());
        String conteudo = """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" \
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" \
                xmlns:script="urn:oasis:names:tc:opendocument:xmlns:script:1.0" \
                xmlns:xlink="http://www.w3.org/1999/xlink" office:version="1.2">
                <office:scripts><office:event-listeners>
                <script:event-listener script:language="ooo:script" script:event-name="dom:load" xlink:type="simple" \
                xlink:href="vnd.sun.star.script:Standard.Module1.Main?language=Basic&amp;location=document"/>
                </office:event-listeners></office:scripts>
                <office:body><office:text><text:p>%s</text:p></office:text></office:body>
                </office:document-content>
                """.formatted(VISIVEL);
        String modulo = """
                <?xml version="1.0" encoding="UTF-8"?>
                <script:module xmlns:script="http://openoffice.org/2000/script" script:name="Module1" \
                script:language="StarBasic">%s</script:module>
                """.formatted(escaparXml(codigo));
        String biblioteca = """
                <?xml version="1.0" encoding="UTF-8"?>
                <library:library xmlns:library="http://openoffice.org/2000/library" library:name="Standard" \
                library:readonly="false" library:passwordprotected="false">
                <library:element library:name="Module1"/></library:library>
                """;
        String bibliotecas = """
                <?xml version="1.0" encoding="UTF-8"?>
                <library:libraries xmlns:library="http://openoffice.org/2000/library" xmlns:xlink="http://www.w3.org/1999/xlink">
                <library:library library:name="Standard" library:link="false"/></library:libraries>
                """;
        String manifesto = """
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
                <manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/>
                <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
                <manifest:file-entry manifest:full-path="Basic/script-lc.xml" manifest:media-type="text/xml"/>
                <manifest:file-entry manifest:full-path="Basic/Standard/script-lb.xml" manifest:media-type="text/xml"/>
                <manifest:file-entry manifest:full-path="Basic/Standard/Module1.xml" manifest:media-type="text/xml"/>
                </manifest:manifest>
                """;
        Map<String, String> entradas = new LinkedHashMap<>();
        entradas.put("META-INF/manifest.xml", manifesto);
        entradas.put("content.xml", conteudo);
        entradas.put("Basic/script-lc.xml", bibliotecas);
        entradas.put("Basic/Standard/script-lb.xml", biblioteca);
        entradas.put("Basic/Standard/Module1.xml", modulo);
        return odf("application/vnd.oasis.opendocument.text", entradas);
    }

    /** DOCX ou XLSX (do POI) com um {@code vbaProject.bin} fictício: assinatura OLE2 e texto de módulo VBA. */
    static byte[] comVbaFicticio(byte[] ooxml, String pasta) throws IOException {
        ByteArrayOutputStream vba = new ByteArrayOutputStream();
        vba.write(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
        vba.write("Attribute VB_Name = \"Module1\"\r\nSub AutoOpen()\r\nShell \"cmd /c calc\"\r\nEnd Sub\r\n"
                .getBytes(StandardCharsets.US_ASCII));
        vba.write(new byte[4096]);
        Map<String, byte[]> extras = new LinkedHashMap<>();
        extras.put(pasta + "/vbaProject.bin", vba.toByteArray());
        return acrescentar(ooxml, extras);
    }

    /**
     * ODS com fórmulas que buscam fora ({@code WEBSERVICE}, referência a arquivo local e a planilha remota) e uma
     * aba vinculada a uma área remota. Os valores em cache são fixos: o PDF deve mostrá-los, sem buscar nada.
     */
    static byte[] odsComVinculosExternos(String url) throws IOException {
        String conteudo = """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" \
                xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" \
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" \
                xmlns:xlink="http://www.w3.org/1999/xlink" office:version="1.2">
                <office:body><office:spreadsheet>
                <table:table table:name="Plan1"><table:table-row>
                <table:table-cell office:value-type="string"><text:p>%2$s</text:p></table:table-cell>
                </table:table-row><table:table-row>
                <table:table-cell table:formula="of:=WEBSERVICE(&quot;%1$sws&quot;)" office:value-type="string" \
                office:string-value="CACHE-WS"><text:p>CACHE-WS</text:p></table:table-cell>
                <table:table-cell table:formula="of:=['file:///etc/passwd'#$Plan1.A1]" office:value-type="string" \
                office:string-value="CACHE-ARQ"><text:p>CACHE-ARQ</text:p></table:table-cell>
                <table:table-cell table:formula="of:=['%1$sexterno.ods'#$Plan1.A1]" office:value-type="string" \
                office:string-value="CACHE-EXT"><text:p>CACHE-EXT</text:p></table:table-cell>
                </table:table-row></table:table>
                <table:table table:name="Vinculo">
                <table:table-source xlink:type="simple" xlink:href="%1$sarea.ods" table:filter-name="calc8" \
                table:mode="copy-all"/>
                <table:table-row><table:table-cell office:value-type="string"><text:p>CACHE-AREA</text:p>\
                </table:table-cell></table:table-row></table:table>
                </office:spreadsheet></office:body></office:document-content>
                """.formatted(url, VISIVEL);
        String manifesto = """
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
                <manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.spreadsheet"/>
                <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
                </manifest:manifest>
                """;
        Map<String, String> entradas = new LinkedHashMap<>();
        entradas.put("META-INF/manifest.xml", manifesto);
        entradas.put("content.xml", conteudo);
        return odf("application/vnd.oasis.opendocument.spreadsheet", entradas);
    }

    /** XLSX com vínculo a pasta de trabalho remota ({@code externalLink}) e fórmula {@code WEBSERVICE}. */
    static byte[] xlsxComVinculoExterno(String url) throws IOException {
        String principal = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        String relacoes = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        Map<String, String> entradas = new LinkedHashMap<>();
        entradas.put("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/externalLinks/externalLink1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.externalLink+xml"/>
                </Types>
                """);
        entradas.put("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="%s/officeDocument" Target="xl/workbook.xml"/></Relationships>
                """.formatted(relacoes));
        entradas.put("xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="%s" xmlns:r="%s"><sheets><sheet name="Plan1" sheetId="1" r:id="rId1"/></sheets>
                <externalReferences><externalReference r:id="rId2"/></externalReferences></workbook>
                """.formatted(principal, relacoes));
        entradas.put("xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="%1$s/worksheet" Target="worksheets/sheet1.xml"/>
                <Relationship Id="rId2" Type="%1$s/externalLink" Target="externalLinks/externalLink1.xml"/>
                </Relationships>
                """.formatted(relacoes));
        entradas.put("xl/worksheets/sheet1.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="%s"><sheetData><row r="1">
                <c r="A1" t="inlineStr"><is><t>%s</t></is></c>
                </row><row r="2">
                <c r="B2"><f>[1]Plan!A1</f><v>7</v></c>
                <c r="C2" t="str"><f>WEBSERVICE("%sws")</f><v>CACHE-WS</v></c>
                </row></sheetData></worksheet>
                """.formatted(principal, VISIVEL, url));
        entradas.put("xl/externalLinks/externalLink1.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <externalLink xmlns="%s" xmlns:r="%s"><externalBook r:id="rId1">
                <sheetNames><sheetName val="Plan"/></sheetNames>
                <sheetDataSet><sheetData sheetId="0"><row r="1"><cell r="A1"><v>7</v></cell></row></sheetData></sheetDataSet>
                </externalBook></externalLink>
                """.formatted(principal, relacoes));
        entradas.put("xl/externalLinks/_rels/externalLink1.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="%s/externalLinkPath" Target="%sexterno.xlsx" TargetMode="External"/>
                </Relationships>
                """.formatted(relacoes, url));
        return zip(entradas);
    }

    /** DOCX com imagem vinculada remota ({@code r:link}) e campos INCLUDEPICTURE e INCLUDETEXT remotos. */
    static byte[] docxComImagemRemota(String url) throws IOException {
        String relacoes = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        Map<String, String> entradas = new LinkedHashMap<>();
        entradas.put("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """);
        entradas.put("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="%s/officeDocument" Target="word/document.xml"/></Relationships>
                """.formatted(relacoes));
        entradas.put("word/_rels/document.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rIdImg" Type="%s/image" Target="%simagem.png" TargetMode="External"/>
                </Relationships>
                """.formatted(relacoes, url));
        entradas.put("word/document.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="%1$s" \
                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" \
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" \
                xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>
                <w:p><w:r><w:t>%3$s</w:t></w:r></w:p>
                <w:p><w:r><w:drawing><wp:inline><wp:extent cx="952500" cy="952500"/><wp:docPr id="1" name="Imagem"/>
                <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic>
                <pic:nvPicPr><pic:cNvPr id="0" name="i"/><pic:cNvPicPr/></pic:nvPicPr>
                <pic:blipFill><a:blip r:link="rIdImg"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="952500" cy="952500"/></a:xfrm>
                <a:prstGeom prst="rect"/></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
                <w:p><w:fldSimple w:instr=" INCLUDEPICTURE &quot;%2$scampo.png&quot; \\d "><w:r><w:t>campo</w:t></w:r></w:fldSimple></w:p>
                <w:p><w:fldSimple w:instr=" INCLUDETEXT &quot;%2$stexto.docx&quot; "><w:r><w:t>texto</w:t></w:r></w:fldSimple></w:p>
                </w:body></w:document>
                """.formatted(relacoes, url, VISIVEL));
        return zip(entradas);
    }

    /**
     * ZIP bomba rotulado como DOCX: estrutura de DOCX e um {@code word/document.xml} que descompacta para
     * {@code descompactado} bytes. Gravado em disco em fluxo (comprimido fica com poucos MB).
     */
    static Path zipBombaComoDocx(Path destino, long descompactado) throws IOException {
        try (OutputStream arquivo = Files.newOutputStream(destino); ZipOutputStream zip = new ZipOutputStream(arquivo)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            byte[] bloco = new byte[1024 * 1024];
            java.util.Arrays.fill(bloco, (byte) ' ');
            for (long escritos = 0; escritos < descompactado; escritos += bloco.length) {
                zip.write(bloco);
            }
            zip.closeEntry();
        }
        return destino;
    }

    /** XLSX com {@code abas} abas de uma célula cada. */
    static byte[] xlsxComAbas(int abas) throws IOException {
        try (XSSFWorkbook pasta = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            for (int i = 0; i < abas; i++) {
                pasta.createSheet("Aba " + i).createRow(0).createCell(0).setCellValue("aba " + i);
            }
            pasta.write(saida);
            return saida.toByteArray();
        }
    }

    /**
     * XLSX com {@code linhas} linhas numa coluna, gravado em fluxo (POI SXSSF) direto no disco. {@code zip64}: o POI grava
     * em ZIP64 por padrão no modo streaming, e o LibreOffice não abre esse formato (docs/10, R39).
     */
    static Path xlsxComLinhas(Path destino, int linhas, boolean zip64) throws IOException {
        try (SXSSFWorkbook pasta = new SXSSFWorkbook(1000); OutputStream saida = Files.newOutputStream(destino)) {
            if (!zip64) {
                pasta.setZip64Mode(org.apache.commons.compress.archivers.zip.Zip64Mode.Never);
            }
            Sheet aba = pasta.createSheet("Linhas");
            for (int i = 0; i < linhas; i++) {
                Row linha = aba.createRow(i);
                linha.createCell(0).setCellValue(i);
            }
            pasta.write(saida);
        }
        return destino;
    }

    // ---- apoio ----

    private static byte[] odf(String mimetype, Map<String, String> entradas) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            byte[] tipo = mimetype.getBytes(StandardCharsets.US_ASCII);
            ZipEntry entrada = new ZipEntry("mimetype");
            entrada.setMethod(ZipEntry.STORED);
            entrada.setSize(tipo.length);
            CRC32 crc = new CRC32();
            crc.update(tipo);
            entrada.setCrc(crc.getValue());
            zip.putNextEntry(entrada);
            zip.write(tipo);
            zip.closeEntry();
            for (Map.Entry<String, String> e : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] zip(Map<String, String> entradas) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> e : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** Copia as entradas de um ZIP e acrescenta {@code extras}. */
    private static byte[] acrescentar(byte[] original, Map<String, byte[]> extras) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(original));
                ZipOutputStream saida = new ZipOutputStream(bytes)) {
            ZipEntry atual;
            while ((atual = entrada.getNextEntry()) != null) {
                saida.putNextEntry(new ZipEntry(atual.getName()));
                entrada.transferTo(saida);
                saida.closeEntry();
            }
            for (Map.Entry<String, byte[]> e : extras.entrySet()) {
                saida.putNextEntry(new ZipEntry(e.getKey()));
                saida.write(e.getValue());
                saida.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String escaparXml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
