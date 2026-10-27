package br.gov.mt.sema.vistas.documento.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Detecta o tipo real pela assinatura dos primeiros bytes. Só o cabeçalho (até {@link #TAMANHO_DO_CABECALHO}) é lido;
 * para ZIP, só a lista de entradas (diretório central) e o {@code mimetype} de ODF, nunca o conteúdo das entradas. A
 * extensão do nome só desempata o contêiner antigo do Office (OLE2), que serve a XLS e DOC.
 */
public final class DetectorDeTipoReal {

    public static final int TAMANHO_DO_CABECALHO = 4096;

    private static final byte[] PDF = ascii("%PDF-");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RTF = ascii("{\\rtf");
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] ZIP = {'P', 'K', 0x03, 0x04};
    private static final int MAXIMO_DO_MIMETYPE = 128;

    private DetectorDeTipoReal() {
    }

    /** Lê o cabeçalho do arquivo em disco e detecta. Arquivo ilegível ou vazio: {@code DESCONHECIDO}. */
    public static TipoDetectado detectar(Path arquivo, String extensaoDoNome) {
        byte[] cabecalho;
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            cabecalho = entrada.readNBytes(TAMANHO_DO_CABECALHO);
        } catch (IOException e) {
            return TipoDetectado.DESCONHECIDO;
        }
        TipoDetectado porCabecalho = detectarPorCabecalho(cabecalho, extensaoDoNome);
        if (porCabecalho != null) {
            return porCabecalho;
        }
        return comecaCom(cabecalho, ZIP) ? detectarZip(arquivo) : TipoDetectado.DESCONHECIDO;
    }

    /**
     * Detecção só pelo cabeçalho, sem acesso a disco. Devolve {@code null} para ZIP (precisa da lista de entradas) e
     * {@code DESCONHECIDO} para o que não se reconhece.
     */
    static TipoDetectado detectarPorCabecalho(byte[] cabecalho, String extensaoDoNome) {
        if (cabecalho == null || cabecalho.length == 0) {
            return TipoDetectado.DESCONHECIDO;
        }
        if (comecaCom(cabecalho, PDF)) {
            return new TipoDetectado(TipoReal.PDF, "pdf");
        }
        if (comecaCom(cabecalho, PNG)) {
            return new TipoDetectado(TipoReal.IMAGEM, "png");
        }
        if (comecaCom(cabecalho, JPEG)) {
            return new TipoDetectado(TipoReal.IMAGEM, "jpg");
        }
        if (comecaCom(cabecalho, RTF)) {
            return new TipoDetectado(TipoReal.TEXTO, "rtf");
        }
        if (comecaCom(cabecalho, OLE2)) {
            return detectarOle2(extensaoDoNome);
        }
        if (comecaCom(cabecalho, ZIP)) {
            return null;
        }
        if (pareceHtml(cabecalho)) {
            return new TipoDetectado(TipoReal.TEXTO, "html");
        }
        return TipoDetectado.DESCONHECIDO;
    }

    /** XLS ou DOC antigos (ou Office criptografado, que também é OLE2): a extensão do nome escolhe a família. */
    private static TipoDetectado detectarOle2(String extensaoDoNome) {
        String extensao = extensaoDoNome == null ? "" : extensaoDoNome.toLowerCase(Locale.ROOT);
        return switch (extensao) {
            case "xls", "xlsx" -> new TipoDetectado(TipoReal.PLANILHA, extensao);
            case "doc", "docx" -> new TipoDetectado(TipoReal.TEXTO, extensao);
            default -> TipoDetectado.DESCONHECIDO;
        };
    }

    private static TipoDetectado detectarZip(Path arquivo) {
        try (ZipFile zip = new ZipFile(arquivo.toFile())) {
            ZipEntry mimetype = zip.getEntry("mimetype");
            if (mimetype != null) {
                TipoDetectado odf = detectarOdf(zip, mimetype);
                if (odf != null) {
                    return odf;
                }
            }
            boolean planilha = false;
            boolean texto = false;
            Enumeration<? extends ZipEntry> entradas = zip.entries();
            while (entradas.hasMoreElements()) {
                String nome = entradas.nextElement().getName();
                planilha |= nome.startsWith("xl/");
                texto |= nome.startsWith("word/");
            }
            if (planilha && !texto) {
                return new TipoDetectado(TipoReal.PLANILHA, "xlsx");
            }
            if (texto && !planilha) {
                return new TipoDetectado(TipoReal.TEXTO, "docx");
            }
            return planilha ? TipoDetectado.DESCONHECIDO : new TipoDetectado(TipoReal.COMPACTADO, "zip");
        } catch (ZipException e) {
            // Começa com "PK" mas o diretório central é inválido (truncado, corrompido): não dá para afirmar o tipo.
            return TipoDetectado.DESCONHECIDO;
        } catch (IOException e) {
            return TipoDetectado.DESCONHECIDO;
        }
    }

    private static TipoDetectado detectarOdf(ZipFile zip, ZipEntry mimetype) throws IOException {
        String conteudo;
        try (InputStream entrada = zip.getInputStream(mimetype)) {
            conteudo = new String(entrada.readNBytes(MAXIMO_DO_MIMETYPE), StandardCharsets.US_ASCII).trim();
        }
        if (conteudo.endsWith("opendocument.spreadsheet")) {
            return new TipoDetectado(TipoReal.PLANILHA, "ods");
        }
        if (conteudo.endsWith("opendocument.text")) {
            return new TipoDetectado(TipoReal.TEXTO, "odt");
        }
        return conteudo.startsWith("application/vnd.oasis.opendocument") ? TipoDetectado.DESCONHECIDO : null;
    }

    private static boolean pareceHtml(byte[] cabecalho) {
        int inicio = cabecalho.length >= 3 && (cabecalho[0] & 0xFF) == 0xEF && (cabecalho[1] & 0xFF) == 0xBB
                && (cabecalho[2] & 0xFF) == 0xBF ? 3 : 0;
        String texto = new String(cabecalho, inicio, cabecalho.length - inicio, StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == 0) {
                return false; // binário: não é HTML
            }
        }
        String semEspacos = texto.stripLeading();
        return semEspacos.startsWith("<!doctype html") || semEspacos.contains("<html");
    }

    private static boolean comecaCom(byte[] dados, byte[] prefixo) {
        if (dados.length < prefixo.length) {
            return false;
        }
        for (int i = 0; i < prefixo.length; i++) {
            if (dados[i] != prefixo[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String texto) {
        return texto.getBytes(StandardCharsets.US_ASCII);
    }
}
