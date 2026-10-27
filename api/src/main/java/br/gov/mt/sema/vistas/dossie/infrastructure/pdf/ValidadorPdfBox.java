package br.gov.mt.sema.vistas.dossie.infrastructure.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.dossie.application.port.ValidadorDePdf;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

/**
 * Valida o arquivo obtido: a assinatura "%PDF-" (o Content-Type do repositório não basta; vale para qualquer
 * documento, inclusive o de tipo não informado), a abertura com cache em
 * disco, a ausência de senha de abertura e a existência de páginas. Qualquer problema é falha do documento (RN09).
 */
@Component
class ValidadorPdfBox implements ValidadorDePdf {

    private static final int JANELA_DA_ASSINATURA = 1024;

    @Override
    public int validar(Path arquivo) {
        if (!temAssinaturaDePdf(arquivo)) {
            throw new FalhaNoDocumentoException("o arquivo não é um PDF (tipo detectado: " + detectarTipo(arquivo) + ")");
        }
        try (PDDocument documento = Loader.loadPDF(arquivo.toFile(), "", null, null,
                IOUtils.createTempFileOnlyStreamCache())) {
            int paginas = documento.getNumberOfPages();
            if (paginas == 0) {
                throw new FalhaNoDocumentoException("o arquivo não tem páginas");
            }
            return paginas;
        } catch (InvalidPasswordException e) {
            throw new FalhaNoDocumentoException("o documento está protegido por senha", e);
        } catch (IOException | RuntimeException e) {
            if (e instanceof FalhaNoDocumentoException falha) {
                throw falha;
            }
            throw new FalhaNoDocumentoException("o arquivo está corrompido", e);
        }
    }

    /** Tipo pelos primeiros bytes; o Content-Type do repositório não é confiável e não chega a esta porta. */
    static String detectarTipo(Path arquivo) {
        byte[] inicio;
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            inicio = entrada.readNBytes(JANELA_DA_ASSINATURA);
        } catch (IOException e) {
            throw new FalhaNoDocumentoException("não foi possível ler o arquivo obtido", e);
        }
        if (inicio.length == 0) {
            return "arquivo vazio";
        }
        if (comecaCom(inicio, 0x50, 0x4B, 0x03, 0x04)) {
            return "ZIP ou documento do Office (docx, xlsx)";
        }
        if (comecaCom(inicio, 0xD0, 0xCF, 0x11, 0xE0)) {
            return "documento do Office antigo (doc, xls)";
        }
        if (comecaCom(inicio, 0x89, 0x50, 0x4E, 0x47)) {
            return "imagem PNG";
        }
        if (comecaCom(inicio, 0xFF, 0xD8, 0xFF)) {
            return "imagem JPEG";
        }
        String texto = new String(inicio, StandardCharsets.ISO_8859_1).stripLeading().toLowerCase(java.util.Locale.ROOT);
        if (texto.startsWith("<!doctype html") || texto.startsWith("<html")) {
            return "HTML";
        }
        for (byte b : inicio) {
            if (b == 0) {
                return "binário não identificado";
            }
        }
        return "texto";
    }

    private static boolean comecaCom(byte[] bytes, int... esperado) {
        if (bytes.length < esperado.length) {
            return false;
        }
        for (int i = 0; i < esperado.length; i++) {
            if ((bytes[i] & 0xFF) != esperado[i]) {
                return false;
            }
        }
        return true;
    }

    /** A especificação aceita o cabeçalho em qualquer ponto dos primeiros 1024 bytes. */
    static boolean temAssinaturaDePdf(Path arquivo) {
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            byte[] inicio = entrada.readNBytes(JANELA_DA_ASSINATURA);
            return new String(inicio, StandardCharsets.ISO_8859_1).contains("%PDF-");
        } catch (IOException e) {
            throw new FalhaNoDocumentoException("não foi possível ler o arquivo obtido", e);
        }
    }
}
