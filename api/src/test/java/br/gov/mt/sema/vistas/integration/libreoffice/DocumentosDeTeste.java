package br.gov.mt.sema.vistas.integration.libreoffice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

/** Documentos sintéticos, gerados por código, para os testes de conversão. Nenhum dado real. */
final class DocumentosDeTeste {

    /** Texto com acentos que precisa aparecer no PDF convertido. */
    static final String TEXTO = "Licença ambiental de teste: ação, emissão e órgão";

    private DocumentosDeTeste() {
    }

    /** DOCX com o texto; tamanho em twips (A4 retrato = 11906 x 16838). */
    static byte[] docx(String texto, int larguraTwips, int alturaTwips) throws IOException {
        try (XWPFDocument documento = new XWPFDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            documento.createParagraph().createRun().setText(texto);
            CTSectPr secao = documento.getDocument().getBody().addNewSectPr();
            CTPageSz pagina = secao.addNewPgSz();
            pagina.setW(BigInteger.valueOf(larguraTwips));
            pagina.setH(BigInteger.valueOf(alturaTwips));
            pagina.setOrient(larguraTwips > alturaTwips ? STPageOrientation.LANDSCAPE : STPageOrientation.PORTRAIT);
            documento.write(saida);
            return saida.toByteArray();
        }
    }

    static byte[] docx(String texto) throws IOException {
        return docx(texto, 11906, 16838);
    }

    /** DOCX criptografado (padrão "agile" do Office) com senha. */
    static byte[] docxCriptografado(String texto) throws Exception {
        byte[] simples = docx(texto);
        try (POIFSFileSystem sistema = new POIFSFileSystem(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
            Encryptor cifra = info.getEncryptor();
            cifra.confirmPassword("senha-de-teste");
            try (OutputStream dados = cifra.getDataStream(sistema)) {
                dados.write(simples);
            }
            sistema.writeFilesystem(saida);
            return saida.toByteArray();
        }
    }

    /** ODT mínimo: mimetype sem compressão como primeira entrada, manifest e content.xml. */
    static byte[] odt(String texto) throws IOException {
        String conteudo = """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" \
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" office:version="1.2">
                <office:body><office:text><text:p>%s</text:p></office:text></office:body>
                </office:document-content>
                """.formatted(texto);
        String manifesto = """
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
                <manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/>
                <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
                </manifest:manifest>
                """;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            byte[] mimetype = "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
            ZipEntry entrada = new ZipEntry("mimetype");
            entrada.setMethod(ZipEntry.STORED);
            entrada.setSize(mimetype.length);
            CRC32 crc = new CRC32();
            crc.update(mimetype);
            entrada.setCrc(crc.getValue());
            zip.putNextEntry(entrada);
            zip.write(mimetype);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/manifest.xml"));
            zip.write(manifesto.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    /** RTF com os acentos em escape de código de página (\'hh, cp1252). */
    static byte[] rtf(String texto) {
        StringBuilder corpo = new StringBuilder();
        for (char c : texto.toCharArray()) {
            corpo.append(c < 128 ? String.valueOf(c) : "\\'" + Integer.toHexString(c));
        }
        return ("{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0 Liberation Sans;}}\\f0\\fs24 " + corpo + "\\par}")
                .getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] html(String corpo) {
        return ("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>t</title></head><body>" + corpo
                + "</body></html>").getBytes(StandardCharsets.UTF_8);
    }

    /** Começa como ZIP, mas o resto é lixo. */
    static byte[] zipCorrompido() {
        byte[] lixo = new byte[4096];
        for (int i = 0; i < lixo.length; i++) {
            lixo[i] = (byte) (i * 31 + 7);
        }
        lixo[0] = 'P';
        lixo[1] = 'K';
        lixo[2] = 3;
        lixo[3] = 4;
        return lixo;
    }

    /**
     * DOC (Word 97) gerado a partir de um DOCX pelo próprio LibreOffice da máquina, por linha de comando e com perfil
     * temporário: não há biblioteca livre que escreva DOC do zero.
     */
    static Path doc(Path pasta, String texto, Path officeHome) throws Exception {
        Path docx = Files.write(pasta.resolve("origem-doc.docx"), docx(texto));
        Path perfil = Files.createDirectories(pasta.resolve("perfil-cli"));
        Path soffice = officeHome.resolve("program").resolve("soffice");
        Process processo = new ProcessBuilder(List.of(soffice.toString(),
                "-env:UserInstallation=" + perfil.toUri(), "--headless", "--norestore",
                "--convert-to", "doc", "--outdir", pasta.toString(), docx.toString()))
                .redirectErrorStream(true).redirectOutput(pasta.resolve("cli.log").toFile()).start();
        if (!processo.waitFor(120, TimeUnit.SECONDS)) {
            processo.destroyForcibly();
            throw new IllegalStateException("soffice não gerou o DOC de teste a tempo");
        }
        Path doc = pasta.resolve("origem-doc.doc");
        if (!Files.isRegularFile(doc)) {
            throw new IllegalStateException("soffice não gerou o DOC de teste");
        }
        return doc;
    }
}
