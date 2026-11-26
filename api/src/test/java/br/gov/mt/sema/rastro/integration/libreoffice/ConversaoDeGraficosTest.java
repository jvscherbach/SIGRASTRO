package br.gov.mt.sema.rastro.integration.libreoffice;

import static br.gov.mt.sema.rastro.integration.libreoffice.ConversorLibreOfficeTest.OFFICE_HOME;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import br.gov.mt.sema.rastro.documento.application.port.ResultadoDaConversao;
import br.gov.mt.sema.rastro.documento.domain.TipoDetectado;
import br.gov.mt.sema.rastro.documento.domain.TipoReal;
import br.gov.mt.sema.rastro.integration.libreoffice.ConversorLibreOfficeTest.Parametros;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gráficos de planilha (V6, bloco 3): pacote sem conteúdo ativo usa o perfil que desenha gráficos; com conteúdo ativo
 * o perfil estrito segue valendo (macro e OLE nunca executados, texto das células convertido) e o resultado avisa que os
 * gráficos ficaram de fora. Exige o LibreOffice; sem ele, cada teste é pulado. Tudo sintético.
 */
@Tag("conversao-libreoffice")
class ConversaoDeGraficosTest {

    private static final String TITULO_DO_GRAFICO = "Serie Sintetica Alfa";
    /** Texto curto (até 6 caracteres), numa coluna alargada na fixture: cabe em qualquer fonte do ambiente. */
    private static final String CELULA = "Celula";

    static Path base;
    static ConversorLibreOffice conversor;

    @TempDir
    Path pasta;

    @BeforeAll
    static void subir() throws IOException {
        if (OFFICE_HOME != null) {
            base = Files.createTempDirectory("conversor-graficos");
            conversor = ConversorLibreOfficeTest.conversor(Parametros.padrao(), OFFICE_HOME, base);
        }
    }

    @AfterAll
    static void descer() {
        if (conversor != null) {
            String marca = conversor.marca();
            conversor.destroy();
            assertThat(ProcessosDoConversor.comMarca(marca)).as("nenhum soffice órfão").isEmpty();
            ConversorLibreOffice.apagarPasta(base);
        }
    }

    @BeforeEach
    void exigir() {
        ConversorLibreOfficeTest.exigirSoffice();
    }

    @Test
    void pacoteSemConteudoAtivoDesenhaOGrafico() throws Exception {
        Convertido c = converter(planilhaComGrafico());

        assertThat(ConversorLibreOfficeTest.texto(c.pdf())).contains(TITULO_DO_GRAFICO);
        assertThat(c.resultado().graficosOmitidos()).isFalse();
    }

    @Test
    void comMacroVbaUsaOPerfilEstritoEAvisaQueOsGraficosFicaramDeFora() throws Exception {
        Convertido c = converter(comEntrada(planilhaComGrafico(), "xl/vbaProject.bin", "nao e macro de verdade"));

        assertThat(ConversorLibreOfficeTest.texto(c.pdf())).contains(CELULA).doesNotContain(TITULO_DO_GRAFICO);
        assertThat(c.resultado().graficosOmitidos()).isTrue();
        assertThat(c.resultado().observacao()).contains("gráficos desta planilha não foram incluídos");
    }

    @Test
    void comObjetoOleEmbutidoUsaOPerfilEstrito() throws Exception {
        Convertido c = converter(comEntrada(planilhaComGrafico(), "xl/embeddings/oleObject1.bin", "qualquer"));

        assertThat(ConversorLibreOfficeTest.texto(c.pdf())).contains(CELULA);
        assertThat(c.resultado().graficosOmitidos()).isTrue();
    }

    @Test
    void comVinculoExternoUsaOPerfilEstrito() throws Exception {
        byte[] pacote = comEntrada(planilhaComGrafico(), "xl/externalLinks/_rels/externalLink1.xml.rels",
                "<Relationships><Relationship Id=\"rId1\" Target=\"file:///inexistente.xlsx\" TargetMode=\"External\"/>"
                        + "</Relationships>");
        Convertido c = converter(pacote);

        assertThat(ConversorLibreOfficeTest.texto(c.pdf())).contains(CELULA);
        assertThat(c.resultado().graficosOmitidos()).isTrue();
    }

    @Test
    void planilhaComumSemGraficosConverteComoAntes() throws Exception {
        Convertido c = converter(PlanilhasDeTeste.xlsx(p -> PlanilhasDeTeste.grade(p, "Dados", 10, 30, 0)));

        assertThat(c.resultado().paginas()).isEqualTo(1);
        assertThat(c.resultado().graficosOmitidos()).isFalse();
        assertThat(ConversorLibreOfficeTest.texto(c.pdf())).contains(PlanilhasDeTeste.marca(10, 30));
    }

    record Convertido(Path pdf, ResultadoDaConversao resultado) {
    }

    private Convertido converter(byte[] xlsx) throws IOException {
        Path pedido = Files.createTempDirectory(pasta, "pedido");
        Path origem = Files.write(pedido.resolve("origem.xlsx"), xlsx);
        Path destino = pedido.resolve("saida.pdf");
        return new Convertido(destino,
                conversor.converter(origem, new TipoDetectado(TipoReal.PLANILHA, "xlsx"), destino));
    }

    /** Aba "Dados" com números e uma aba "Graficos" só com um gráfico de linha sobre eles, sem valores em cache. */
    private static byte[] planilhaComGrafico() throws IOException {
        try (XSSFWorkbook pasta = new XSSFWorkbook()) {
            Sheet dados = pasta.createSheet("Dados");
            dados.setColumnWidth(0, 20 * 256);
            dados.setColumnWidth(1, 12 * 256);
            Row cabecalho = dados.createRow(0);
            cabecalho.createCell(0).setCellValue(CELULA);
            cabecalho.createCell(1).setCellValue("Valor");
            for (int i = 1; i <= 5; i++) {
                Row linha = dados.createRow(i);
                linha.createCell(0).setCellValue("P" + i);
                linha.createCell(1).setCellValue(i * 10.0);
            }
            XSSFDrawing desenho = pasta.createSheet("Graficos").createDrawingPatriarch();
            XSSFClientAnchor ancora = desenho.createAnchor(0, 0, 0, 0, 0, 0, 8, 15);
            XSSFChart grafico = desenho.createChart(ancora);
            grafico.setTitleText(TITULO_DO_GRAFICO);
            XDDFCategoryAxis inferior = grafico.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis esquerdo = grafico.createValueAxis(AxisPosition.LEFT);
            XDDFLineChartData linhas = (XDDFLineChartData) grafico.createData(ChartTypes.LINE, inferior, esquerdo);
            XDDFDataSource<String> categorias =
                    XDDFDataSourcesFactory.fromStringCellRange((org.apache.poi.xssf.usermodel.XSSFSheet) dados,
                            new CellRangeAddress(1, 5, 0, 0));
            XDDFNumericalDataSource<Double> valores =
                    XDDFDataSourcesFactory.fromNumericCellRange((org.apache.poi.xssf.usermodel.XSSFSheet) dados,
                            new CellRangeAddress(1, 5, 1, 1));
            linhas.addSeries(categorias, valores);
            grafico.plot(linhas);
            try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
                pasta.write(saida);
                return semCache(saida.toByteArray());
            }
        }
    }

    /** Tira os valores em cache dos gráficos (como no caso real): o LibreOffice precisa ler as células. */
    private static byte[] semCache(byte[] xlsx) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(xlsx));
                ZipOutputStream zip = new ZipOutputStream(saida)) {
            for (ZipEntry e = entrada.getNextEntry(); e != null; e = entrada.getNextEntry()) {
                byte[] bytes = entrada.readAllBytes();
                if (e.getName().startsWith("xl/charts/chart")) {
                    String xml = new String(bytes, StandardCharsets.UTF_8)
                            .replaceAll("(?s)<c:(num|str)Cache>.*?</c:(num|str)Cache>", "");
                    bytes = xml.getBytes(StandardCharsets.UTF_8);
                }
                zip.putNextEntry(new ZipEntry(e.getName()));
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return saida.toByteArray();
    }

    /** Acrescenta uma entrada qualquer ao pacote (sem macro real, sem OLE real). */
    private static byte[] comEntrada(byte[] xlsx, String nome, String conteudo) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(xlsx));
                ZipOutputStream zip = new ZipOutputStream(saida)) {
            for (ZipEntry e = entrada.getNextEntry(); e != null; e = entrada.getNextEntry()) {
                zip.putNextEntry(new ZipEntry(e.getName()));
                zip.write(entrada.readAllBytes());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(nome));
            zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return saida.toByteArray();
    }
}
