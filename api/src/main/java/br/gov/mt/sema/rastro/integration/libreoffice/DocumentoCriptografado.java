package br.gov.mt.sema.rastro.integration.libreoffice;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Reconhece o DOCX/XLSX criptografado com senha antes de entregá-lo ao LibreOffice. O Office guarda esse arquivo num
 * contêiner OLE2 com a entrada {@code EncryptedPackage}; o LibreOffice aborta a detecção de tipo dele ("type detection
 * aborted") em vez de pedir a senha, e a falha pareceria arquivo corrompido. DOC/XLS antigos e ODF com senha são
 * reconhecidos pelo próprio LibreOffice, que pede a senha (o JODConverter traduz em PasswordProtectedException).
 *
 * <p>Lê só o cabeçalho (512 bytes) e o primeiro setor do diretório, onde o Office grava essas entradas.
 */
final class DocumentoCriptografado {

    private static final long ASSINATURA_OLE2 = 0xE11AB1A1E011CFD0L;
    private static final byte[] ENTRADA = "EncryptedPackage".getBytes(StandardCharsets.UTF_16LE);
    private static final int TAMANHO_MAXIMO_DO_SETOR = 4096;

    private DocumentoCriptografado() {
    }

    static boolean ooxmlCriptografado(Path arquivo) {
        try (FileChannel canal = FileChannel.open(arquivo, StandardOpenOption.READ)) {
            ByteBuffer cabecalho = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
            if (canal.read(cabecalho, 0) < 512 || cabecalho.getLong(0) != ASSINATURA_OLE2) {
                return false;
            }
            int deslocamento = cabecalho.getShort(0x1E);
            if (deslocamento < 7 || deslocamento > 12) {
                return false;
            }
            int tamanhoDoSetor = 1 << deslocamento;
            long primeiroSetorDoDiretorio = Integer.toUnsignedLong(cabecalho.getInt(0x30));
            ByteBuffer diretorio = ByteBuffer.allocate(Math.min(tamanhoDoSetor, TAMANHO_MAXIMO_DO_SETOR));
            canal.read(diretorio, (primeiroSetorDoDiretorio + 1) * tamanhoDoSetor);
            return contem(diretorio.array(), diretorio.position(), ENTRADA);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean contem(byte[] dados, int tamanho, byte[] procurado) {
        for (int i = 0; i + procurado.length <= tamanho; i++) {
            int j = 0;
            while (j < procurado.length && dados[i + j] == procurado[j]) {
                j++;
            }
            if (j == procurado.length) {
                return true;
            }
        }
        return false;
    }
}
