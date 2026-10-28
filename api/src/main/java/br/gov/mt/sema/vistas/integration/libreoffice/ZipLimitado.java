package br.gov.mt.sema.vistas.integration.libreoffice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.Motivo;

/**
 * Barra "ZIP bomba" antes do LibreOffice (S3, bloco 8): DOCX, XLSX, ODT e ODS são ZIP, e o conversor descompactaria
 * tudo em memória. As entradas são descompactadas aqui, em fluxo e sem guardar nada, contando os bytes reais (o
 * tamanho declarado no cabeçalho pode mentir); passou do limite, a leitura para na hora.
 */
final class ZipLimitado {

    /** Total descompactado aceito. Uma planilha real de 1 milhão de linhas fica bem abaixo disto. */
    static final long DESCOMPACTADO_MAXIMO = 1024L * 1024 * 1024;

    /** Entradas aceitas (uma planilha de milhares de abas tem algumas por aba). */
    static final int ENTRADAS_MAXIMAS = 20_000;

    private static final byte[] ZIP = {'P', 'K', 0x03, 0x04};

    private ZipLimitado() {
    }

    /** Arquivo que não é ZIP passa direto; ZIP ilegível é {@code CORROMPIDO}; acima dos limites, {@code ENTRADA_GRANDE_DEMAIS}. */
    static void conferir(Path arquivo) throws IOException {
        conferir(arquivo, DESCOMPACTADO_MAXIMO, ENTRADAS_MAXIMAS);
    }

    static void conferir(Path arquivo, long maximo, int entradasMaximas) throws IOException {
        if (!ehZip(arquivo)) {
            return;
        }
        long total = 0;
        int entradas = 0;
        byte[] bloco = new byte[64 * 1024];
        try (ZipFile zip = new ZipFile(arquivo.toFile())) {
            Enumeration<? extends ZipEntry> todas = zip.entries();
            while (todas.hasMoreElements()) {
                ZipEntry entrada = todas.nextElement();
                if (++entradas > entradasMaximas) {
                    throw new FalhaDeConversaoException(Motivo.ENTRADA_GRANDE_DEMAIS);
                }
                try (InputStream fluxo = zip.getInputStream(entrada)) {
                    int lidos;
                    while ((lidos = fluxo.read(bloco)) >= 0) {
                        total += lidos;
                        if (total > maximo) {
                            throw new FalhaDeConversaoException(Motivo.ENTRADA_GRANDE_DEMAIS);
                        }
                    }
                }
            }
        } catch (ZipException e) {
            throw new FalhaDeConversaoException(Motivo.CORROMPIDO, e);
        }
    }

    private static boolean ehZip(Path arquivo) throws IOException {
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            byte[] cabecalho = entrada.readNBytes(ZIP.length);
            return java.util.Arrays.equals(cabecalho, ZIP);
        }
    }
}
