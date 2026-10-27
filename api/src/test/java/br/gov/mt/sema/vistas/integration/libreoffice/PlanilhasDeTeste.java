package br.gov.mt.sema.vistas.integration.libreoffice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Planilhas sintéticas geradas pelo Apache POI (escopo de teste) para os testes de conversão. Células com marcas
 * {@code C<coluna>L<linha>} (1-based), distintas entre si, para conferir no texto extraído do PDF que a última coluna e a
 * última linha aparecem. Nenhum dado real.
 */
final class PlanilhasDeTeste {

    private PlanilhasDeTeste() {
    }

    static String marca(int coluna, int linha) {
        return "C" + coluna + "L" + linha;
    }

    static byte[] xlsx(Consumer<Workbook> montar) throws IOException {
        try (Workbook pasta = new XSSFWorkbook()) {
            return gravar(pasta, montar);
        }
    }

    /** XLS (BIFF8, contêiner OLE2) com o mesmo conteúdo que {@link #xlsx}. */
    static byte[] xls(Consumer<Workbook> montar) throws IOException {
        try (Workbook pasta = new HSSFWorkbook()) {
            return gravar(pasta, montar);
        }
    }

    private static byte[] gravar(Workbook pasta, Consumer<Workbook> montar) throws IOException {
        montar.accept(pasta);
        pasta.getCreationHelper().createFormulaEvaluator().evaluateAll(); // grava o valor calculado das fórmulas
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            pasta.write(saida);
            return saida.toByteArray();
        }
    }

    /** Aba com {@code colunas} x {@code linhas} marcas; {@code larguraEmCaracteres} 0 mantém a largura padrão. */
    static Sheet grade(Workbook pasta, String nome, int colunas, int linhas, int larguraEmCaracteres) {
        Sheet aba = pasta.createSheet(nome);
        for (int l = 0; l < linhas; l++) {
            Row linha = aba.createRow(l);
            for (int c = 0; c < colunas; c++) {
                linha.createCell(c).setCellValue(marca(c + 1, l + 1));
            }
        }
        if (larguraEmCaracteres > 0) {
            for (int c = 0; c < colunas; c++) {
                aba.setColumnWidth(c, larguraEmCaracteres * 256);
            }
        }
        return aba;
    }

    static void texto(Sheet aba, int linha, int coluna, String texto) {
        Row r = aba.getRow(linha) == null ? aba.createRow(linha) : aba.getRow(linha);
        r.createCell(coluna).setCellValue(texto);
    }

    static void textoComQuebra(Workbook pasta, Sheet aba, int linha, int coluna, String texto) {
        texto(aba, linha, coluna, texto);
        CellStyle quebra = pasta.createCellStyle();
        quebra.setWrapText(true);
        aba.getRow(linha).getCell(coluna).setCellStyle(quebra);
    }
}
