package br.gov.mt.sema.rastro.documento.domain;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Descobre a família (planilha ou texto) de um contêiner OLE2 (Office antigo) pelos <b>nomes dos fluxos do
 * diretório</b>: {@code Workbook}/{@code Book} é XLS e {@code WordDocument} é DOC. Só lê o cabeçalho, a tabela de
 * alocação e os setores do diretório; nunca o conteúdo dos fluxos. Tudo é limitado: arquivo malformado ou fora dos
 * limites resulta em "não identificado", nunca em exceção.
 */
final class FamiliaOle2 {

    enum Familia { PLANILHA, TEXTO, NAO_IDENTIFICADA }

    private static final int TAMANHO_DO_CABECALHO = 512;
    private static final int ENTRADAS_NO_CABECALHO = 109;
    private static final int TAMANHO_DA_ENTRADA = 128;
    private static final int LIMITE_DE_SETORES_DO_DIRETORIO = 4096;
    private static final int LIMITE_DE_SETORES_DA_FAT = 4096;
    private static final int FIM_DA_CADEIA = 0xFFFFFFFE;

    private FamiliaOle2() {
    }

    static Familia identificar(Path arquivo) {
        try (RandomAccessFile f = new RandomAccessFile(arquivo.toFile(), "r")) {
            return identificar(f);
        } catch (IOException | RuntimeException e) {
            return Familia.NAO_IDENTIFICADA;
        }
    }

    private static Familia identificar(RandomAccessFile f) throws IOException {
        long tamanho = f.length();
        byte[] cabecalho = new byte[TAMANHO_DO_CABECALHO];
        if (tamanho < TAMANHO_DO_CABECALHO) {
            return Familia.NAO_IDENTIFICADA;
        }
        f.readFully(cabecalho);
        ByteBuffer h = ByteBuffer.wrap(cabecalho).order(ByteOrder.LITTLE_ENDIAN);
        int deslocamento = h.getShort(0x1E) & 0xFFFF;
        if (deslocamento != 9 && deslocamento != 12) {
            return Familia.NAO_IDENTIFICADA;
        }
        int tamanhoDoSetor = 1 << deslocamento;
        int totalDaFat = h.getInt(0x2C);
        int primeiroDoDiretorio = h.getInt(0x30);
        int primeiroDifat = h.getInt(0x44);
        int totalDifat = h.getInt(0x48);
        if (totalDaFat <= 0 || totalDaFat > LIMITE_DE_SETORES_DA_FAT) {
            return Familia.NAO_IDENTIFICADA;
        }

        List<Integer> setoresDaFat = new ArrayList<>();
        for (int i = 0; i < ENTRADAS_NO_CABECALHO && setoresDaFat.size() < totalDaFat; i++) {
            setoresDaFat.add(h.getInt(0x4C + 4 * i));
        }
        int difat = primeiroDifat;
        for (int n = 0; n < totalDifat && setoresDaFat.size() < totalDaFat && difat >= 0; n++) {
            ByteBuffer d = ler(f, tamanho, tamanhoDoSetor, difat);
            if (d == null) {
                return Familia.NAO_IDENTIFICADA;
            }
            int porSetor = tamanhoDoSetor / 4 - 1;
            for (int i = 0; i < porSetor && setoresDaFat.size() < totalDaFat; i++) {
                setoresDaFat.add(d.getInt(4 * i));
            }
            difat = d.getInt(4 * porSetor);
        }

        int entradasPorSetorDaFat = tamanhoDoSetor / 4;
        int setor = primeiroDoDiretorio;
        for (int n = 0; n < LIMITE_DE_SETORES_DO_DIRETORIO && setor >= 0; n++) {
            ByteBuffer dir = ler(f, tamanho, tamanhoDoSetor, setor);
            if (dir == null) {
                return Familia.NAO_IDENTIFICADA;
            }
            Familia achada = examinar(dir, tamanhoDoSetor);
            if (achada != Familia.NAO_IDENTIFICADA) {
                return achada;
            }
            int indiceDaFat = setor / entradasPorSetorDaFat;
            if (indiceDaFat >= setoresDaFat.size()) {
                return Familia.NAO_IDENTIFICADA;
            }
            ByteBuffer fat = ler(f, tamanho, tamanhoDoSetor, setoresDaFat.get(indiceDaFat));
            if (fat == null) {
                return Familia.NAO_IDENTIFICADA;
            }
            int proximo = fat.getInt(4 * (setor % entradasPorSetorDaFat));
            if (proximo == FIM_DA_CADEIA) {
                break;
            }
            setor = proximo;
        }
        return Familia.NAO_IDENTIFICADA;
    }

    private static Familia examinar(ByteBuffer dir, int tamanhoDoSetor) {
        for (int base = 0; base + TAMANHO_DA_ENTRADA <= tamanhoDoSetor; base += TAMANHO_DA_ENTRADA) {
            int bytesDoNome = dir.getShort(base + 0x40) & 0xFFFF;
            int tipo = dir.get(base + 0x42);
            if (tipo != 2 || bytesDoNome < 4 || bytesDoNome > 64) { // 2 = fluxo
                continue;
            }
            byte[] nome = new byte[bytesDoNome - 2]; // sem o terminador
            dir.position(base);
            dir.get(nome);
            String texto = new String(nome, StandardCharsets.UTF_16LE);
            if (texto.equals("Workbook") || texto.equals("Book")) {
                return Familia.PLANILHA;
            }
            if (texto.equals("WordDocument")) {
                return Familia.TEXTO;
            }
        }
        return Familia.NAO_IDENTIFICADA;
    }

    /** Setor {@code n} (o setor 0 começa depois do cabeçalho, que ocupa o equivalente a um setor). */
    private static ByteBuffer ler(RandomAccessFile f, long tamanho, int tamanhoDoSetor, int n) throws IOException {
        long posicao = (long) (n + 1) * tamanhoDoSetor;
        if (n < 0 || posicao + tamanhoDoSetor > tamanho) {
            return null;
        }
        byte[] dados = new byte[tamanhoDoSetor];
        f.seek(posicao);
        f.readFully(dados);
        return ByteBuffer.wrap(dados).order(ByteOrder.LITTLE_ENDIAN);
    }
}
