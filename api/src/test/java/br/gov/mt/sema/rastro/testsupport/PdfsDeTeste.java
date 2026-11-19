package br.gov.mt.sema.rastro.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Gera PDFs pequenos para os testes do dossiê. */
public final class PdfsDeTeste {

    private PdfsDeTeste() {
    }

    /** PDF com {@code paginas} páginas do tamanho e da rotação dados; cada página traz "texto - pagina N". */
    public static Path criar(Path destino, int paginas, PDRectangle tamanho, int rotacao, String texto) {
        try (PDDocument documento = new PDDocument()) {
            PDType1Font fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int i = 1; i <= paginas; i++) {
                PDPage pagina = new PDPage(tamanho);
                pagina.setRotation(rotacao);
                documento.addPage(pagina);
                try (PDPageContentStream c = new PDPageContentStream(documento, pagina)) {
                    c.beginText();
                    c.setFont(fonte, 14);
                    c.newLineAtOffset(60, tamanho.getHeight() - 80);
                    c.showText(texto + " - pagina " + i);
                    c.endText();
                }
            }
            documento.save(destino.toFile());
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Páginas sem nenhum conteúdo: permitem medir só o que o dossiê desenha (rodapé). */
    public static Path criarEmBranco(Path destino, int paginas, PDRectangle tamanho, int rotacao) {
        try (PDDocument documento = new PDDocument()) {
            for (int i = 0; i < paginas; i++) {
                PDPage pagina = new PDPage(tamanho);
                pagina.setRotation(rotacao);
                documento.addPage(pagina);
            }
            documento.save(destino.toFile());
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path criarA4(Path destino, int paginas, String texto) {
        return criar(destino, paginas, PDRectangle.A4, 0, texto);
    }

    public static byte[] bytes(int paginas, String texto) {
        try {
            Path temporario = Files.createTempFile("pdf-teste", ".pdf");
            try {
                criarA4(temporario, paginas, texto);
                return Files.readAllBytes(temporario);
            } finally {
                Files.deleteIfExists(temporario);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF que exige senha para abrir. */
    public static Path comSenha(Path destino) {
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage(PDRectangle.A4));
            StandardProtectionPolicy politica = new StandardProtectionPolicy("dono-de-teste", "usuario-de-teste",
                    new AccessPermission());
            politica.setEncryptionKeyLength(128);
            documento.protect(politica);
            documento.save(destino.toFile());
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF sem nenhuma página. */
    public static Path semPaginas(Path destino) {
        try (PDDocument documento = new PDDocument()) {
            documento.save(destino.toFile());
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Começa como PDF e termina em lixo: a assinatura existe, mas o arquivo não abre. */
    public static Path corrompido(Path destino) {
        try {
            Files.writeString(destino, "%PDF-1.7\n1 0 obj\n<< /Type /Catalog /Pages 99 0 R >>\nlixo sem trailer", StandardCharsets.ISO_8859_1);
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Conteúdo que não é PDF (por exemplo, uma página de erro HTML devolvida pelo repositório). */
    public static Path html(Path destino) {
        try {
            Files.writeString(destino, "<html><body>Erro</body></html>", StandardCharsets.UTF_8);
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
