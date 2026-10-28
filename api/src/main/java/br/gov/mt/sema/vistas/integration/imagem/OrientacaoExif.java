package br.gov.mt.sema.vistas.integration.imagem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lê a orientação EXIF (tag 0x0112) de um JPEG, olhando só os primeiros bytes do arquivo. Qualquer irregularidade
 * (EXIF ausente, truncado, valor fora de 1 a 8) resulta em 1, "sem rotação".
 */
final class OrientacaoExif {

    private static final int LIMITE_DE_LEITURA = 128 * 1024;
    static final int SEM_ROTACAO = 1;

    private OrientacaoExif() {
    }

    static int de(Path jpeg) {
        byte[] b;
        try (InputStream entrada = Files.newInputStream(jpeg)) {
            b = entrada.readNBytes(LIMITE_DE_LEITURA);
        } catch (IOException e) {
            return SEM_ROTACAO;
        }
        if (b.length < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8) {
            return SEM_ROTACAO;
        }
        int posicao = 2;
        while (posicao + 4 <= b.length && u8(b, posicao) == 0xFF) {
            int marcador = u8(b, posicao + 1);
            if (marcador == 0xFF) { // preenchimento
                posicao++;
                continue;
            }
            if (marcador == 0xDA || marcador == 0xD9) { // início da imagem ou fim: acabaram os metadados
                return SEM_ROTACAO;
            }
            int tamanho = (u8(b, posicao + 2) << 8) | u8(b, posicao + 3);
            if (tamanho < 2) {
                return SEM_ROTACAO;
            }
            if (marcador == 0xE1 && posicao + 10 <= b.length && ehExif(b, posicao + 4)) {
                int fim = Math.min(b.length, posicao + 2 + tamanho);
                return doTiff(b, posicao + 10, fim);
            }
            posicao += 2 + tamanho;
        }
        return SEM_ROTACAO;
    }

    private static boolean ehExif(byte[] b, int inicio) {
        return b[inicio] == 'E' && b[inicio + 1] == 'x' && b[inicio + 2] == 'i' && b[inicio + 3] == 'f'
                && b[inicio + 4] == 0 && b[inicio + 5] == 0;
    }

    private static int doTiff(byte[] b, int tiff, int fim) {
        if (tiff + 8 > fim) {
            return SEM_ROTACAO;
        }
        boolean little;
        if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
            little = true;
        } else if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
            little = false;
        } else {
            return SEM_ROTACAO;
        }
        long ifd = tiff + u32(b, tiff + 4, little);
        if (ifd < tiff || ifd + 2 > fim) {
            return SEM_ROTACAO;
        }
        int entradas = u16(b, (int) ifd, little);
        for (int i = 0; i < entradas; i++) {
            int entrada = (int) ifd + 2 + i * 12;
            if (entrada + 12 > fim) {
                return SEM_ROTACAO;
            }
            if (u16(b, entrada, little) == 0x0112) {
                int valor = u16(b, entrada + 8, little);
                return valor >= 1 && valor <= 8 ? valor : SEM_ROTACAO;
            }
        }
        return SEM_ROTACAO;
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int u16(byte[] b, int i, boolean little) {
        return little ? u8(b, i) | (u8(b, i + 1) << 8) : (u8(b, i) << 8) | u8(b, i + 1);
    }

    private static long u32(byte[] b, int i, boolean little) {
        long alto = u16(b, little ? i + 2 : i, little);
        long baixo = u16(b, little ? i : i + 2, little);
        return (alto << 16) | baixo;
    }
}
