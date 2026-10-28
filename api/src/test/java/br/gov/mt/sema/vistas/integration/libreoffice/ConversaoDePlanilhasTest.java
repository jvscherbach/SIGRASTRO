package br.gov.mt.sema.vistas.integration.libreoffice;

import static br.gov.mt.sema.vistas.integration.libreoffice.ConversorLibreOfficeTest.OFFICE_HOME;
import static br.gov.mt.sema.vistas.integration.libreoffice.PlanilhasDeTeste.marca;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao.PlanilhaModo;
import br.gov.mt.sema.vistas.documento.application.port.ResultadoDaConversao;
import br.gov.mt.sema.vistas.documento.domain.TipoDetectado;
import br.gov.mt.sema.vistas.documento.domain.TipoReal;
import br.gov.mt.sema.vistas.integration.libreoffice.ConversorLibreOfficeTest.Parametros;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Planilhas (docs/06): nenhuma coluna ou linha com conteúdo cortada. Qualidade conferida objetivamente pelo texto
 * extraído do PDF (a última coluna e a última linha aparecem), pelo número de páginas e pela orientação. Exige o
 * LibreOffice; sem ele, cada teste é pulado com aviso. Planilhas geradas por código (Apache POI).
 */
class ConversaoDePlanilhasTest {

    static Path base;
    static Parametros parametros;
    static ConversorLibreOffice conversor;

    @TempDir
    Path pasta;

    record Convertido(Path pdf, ResultadoDaConversao resultado) {
    }

    @BeforeAll
    static void subir() throws IOException {
        if (OFFICE_HOME != null) {
            base = Files.createTempDirectory("conversor-planilhas");
            parametros = Parametros.padrao();
            conversor = ConversorLibreOfficeTest.conversor(parametros, OFFICE_HOME, base);
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
    void exigirSofficeEPadroes() {
        ConversorLibreOfficeTest.exigirSoffice();
        parametros.planilhaModo = PlanilhaModo.LARGURA;
        parametros.abasOcultas = false;
        parametros.planilhaEscalaMinima = 40;
        parametros.paginasMaximas = 2000;
    }

    private static final Consumer<Workbook> QUARENTA_COLUNAS =
            p -> PlanilhasDeTeste.grade(p, "Dados", 40, 30, 6);

    @Test
    void quarentaColunasCabemNaLarguraDeUmaPaginaPaisagem() throws Exception {
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(QUARENTA_COLUNAS));

        String texto = texto(c.pdf());
        for (int coluna = 1; coluna <= 40; coluna++) {
            assertThat(texto).contains(marca(coluna, 30)); // última linha, todas as colunas
        }
        assertThat(texto).contains(marca(40, 1));
        assertThat(c.resultado().paginas()).isEqualTo(1);
        assertThat(c.resultado().abasEmPaginaUnica()).isZero();
        assertThat(paisagem(c.pdf(), 0)).isTrue();
    }

    @Test
    void xlsAntigoTemOMesmoResultadoQueXlsx() throws Exception {
        Convertido xlsx = converter("xlsx", PlanilhasDeTeste.xlsx(QUARENTA_COLUNAS));
        Convertido xls = converter("xls", PlanilhasDeTeste.xls(QUARENTA_COLUNAS));

        assertThat(xls.resultado()).isEqualTo(xlsx.resultado());
        assertThat(paisagem(xls.pdf(), 0)).isTrue();
        assertThat(marcas(texto(xls.pdf()), 40, 30)).isEqualTo(marcas(texto(xlsx.pdf()), 40, 30));
    }

    @Test
    void odsTambemEAjustado() throws Exception {
        Path ods = DocumentosDeTeste.pelaLinhaDeComando(Files.createDirectories(pasta.resolve("gerar-ods")),
                "origem.xlsx", PlanilhasDeTeste.xlsx(QUARENTA_COLUNAS), "ods", OFFICE_HOME.toPath());
        Convertido c = converter("ods", Files.readAllBytes(ods));

        assertThat(texto(c.pdf())).contains(marca(40, 30)).contains(marca(1, 30));
        assertThat(c.resultado().paginas()).isEqualTo(1);
    }

    @Test
    void planilhaLargaDemaisVaiParaPaginaUnicaSemCortar() throws Exception {
        // 120 colunas na largura padrão: na largura de A4 paisagem a escala ficaria bem abaixo de 40%.
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> PlanilhasDeTeste.grade(p, "Larga", 120, 10, 0)));

        assertThat(c.resultado().abasEmPaginaUnica()).isEqualTo(1);
        assertThat(c.resultado().observacao()).contains("1 aba(s) em página única");
        assertThat(c.resultado().paginas()).isEqualTo(1);
        assertThat(texto(c.pdf())).contains(marca(120, 10)).contains(marca(1, 10)).contains(marca(120, 1));
        assertThat(paisagem(c.pdf(), 0)).isTrue();
    }

    @Test
    void comEscalaMinimaZeroAMesmaPlanilhaFicaNaLarguraEmVariasPaginas() throws Exception {
        parametros.planilhaEscalaMinima = 0;
        Convertido c = converter("xlsx",
                PlanilhasDeTeste.xlsx(p -> PlanilhasDeTeste.grade(p, "Larga", 120, 400, 0)));

        assertThat(c.resultado().abasEmPaginaUnica()).isZero();
        assertThat(c.resultado().paginas()).isGreaterThan(1);
        assertThat(texto(c.pdf())).contains(marca(120, 400));
    }

    @Test
    void estreitaEAltaFicaEmRetratoEmVariasPaginas() throws Exception {
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> PlanilhasDeTeste.grade(p, "Alta", 3, 300, 0)));

        assertThat(paisagem(c.pdf(), 0)).isFalse();
        assertThat(c.resultado().paginas()).isGreaterThan(1);
        assertThat(texto(c.pdf())).contains(marca(3, 300)).contains(marca(3, 1));
    }

    @Test
    void abaOcultaFicaForaEEntraQuandoConfigurada() throws Exception {
        byte[] planilha = PlanilhasDeTeste.xlsx(p -> {
            PlanilhasDeTeste.texto(p.createSheet("Visivel"), 0, 0, "ABAVISIVEL");
            PlanilhasDeTeste.texto(p.createSheet("Oculta"), 0, 0, "ABAOCULTA");
            p.setSheetHidden(1, true);
        });

        Convertido padrao = converter("xlsx", planilha);
        assertThat(texto(padrao.pdf())).contains("ABAVISIVEL").doesNotContain("ABAOCULTA");
        assertThat(padrao.resultado().paginas()).isEqualTo(1);

        parametros.abasOcultas = true;
        Convertido comOcultas = converter("xlsx", planilha);
        assertThat(texto(comOcultas.pdf())).contains("ABAVISIVEL").contains("ABAOCULTA");
        assertThat(comOcultas.resultado().paginas()).isEqualTo(2);
    }

    @Test
    void linhasEColunasOcultasNaoAparecemComoOUsuarioVe() throws Exception {
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> {
            Sheet aba = PlanilhasDeTeste.grade(p, "Dados", 4, 4, 0);
            PlanilhasDeTeste.texto(aba, 4, 0, "LINHAOCULTA");
            aba.getRow(4).setZeroHeight(true);
            PlanilhasDeTeste.texto(aba, 0, 4, "COLUNAOCULTA");
            aba.setColumnHidden(4, true);
            PlanilhasDeTeste.texto(aba, 5, 0, "DEPOISDAOCULTA");
        }));

        assertThat(texto(c.pdf())).contains(marca(4, 4)).contains("DEPOISDAOCULTA")
                .doesNotContain("LINHAOCULTA").doesNotContain("COLUNAOCULTA");
    }

    @Test
    void cadaAbaComecaEmPaginaNovaEAbaVaziaNaoGeraPagina() throws Exception {
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> {
            PlanilhasDeTeste.texto(p.createSheet("Primeira"), 0, 0, "PRIMEIRAABA");
            p.createSheet("Vazia");
            PlanilhasDeTeste.texto(p.createSheet("Terceira"), 0, 0, "TERCEIRAABA");
        }));

        assertThat(c.resultado().paginas()).isEqualTo(2);
        assertThat(textoDaPagina(c.pdf(), 1)).contains("PRIMEIRAABA").doesNotContain("TERCEIRAABA");
        assertThat(textoDaPagina(c.pdf(), 2)).contains("TERCEIRAABA");
    }

    @Test
    void areaDeImpressaoDoArquivoERespeitada() throws Exception {
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> {
            Sheet aba = p.createSheet("Dados");
            PlanilhasDeTeste.texto(aba, 0, 0, "DENTRODAAREA");
            PlanilhasDeTeste.texto(aba, 9, 5, "FORADAAREA");
            p.setPrintArea(0, "$A$1:$B$2");
        }));

        assertThat(texto(c.pdf())).contains("DENTRODAAREA").doesNotContain("FORADAAREA");
    }

    @Test
    void formulaSaiComOValorCalculadoETextoLongoComQuebraSaiInteiro() throws Exception {
        String longo = "Texto longo da célula com quebra automática que precisa aparecer inteiro no PDF convertido";
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> {
            Sheet aba = p.createSheet("Dados");
            aba.createRow(0).createCell(0).setCellValue(21);
            aba.createRow(1).createCell(0).setCellFormula("A1*2");
            PlanilhasDeTeste.textoComQuebra(p, aba, 2, 0, longo);
            aba.setColumnWidth(0, 15 * 256);
        }));

        String texto = texto(c.pdf());
        assertThat(texto).contains("42").doesNotContain("A1*2");
        for (String palavra : longo.split(" ")) {
            assertThat(texto).contains(palavra);
        }
    }

    @Test
    void planilhaSemConteudoFalhaEmVezDePdfEmBranco() throws Exception {
        byte[] vazia = PlanilhasDeTeste.xlsx(p -> {
            p.createSheet("Uma");
            p.createSheet("Outra");
        });
        assertFalha("xlsx", vazia, Motivo.PLANILHA_VAZIA);

        byte[] soOculta = PlanilhasDeTeste.xlsx(p -> {
            p.createSheet("Vazia");
            PlanilhasDeTeste.texto(p.createSheet("Oculta"), 0, 0, "SOOCULTA");
            p.setSheetHidden(1, true);
        });
        assertFalha("xlsx", soOculta, Motivo.PLANILHA_VAZIA);
    }

    @Test
    void planilhaEnormeAcimaDoLimiteDePaginasFalha() throws Exception {
        parametros.paginasMaximas = 3;
        assertFalha("xlsx", PlanilhasDeTeste.xlsx(p -> PlanilhasDeTeste.grade(p, "Enorme", 3, 2000, 0)),
                Motivo.PAGINAS_DEMAIS);
    }

    @Test
    void modoPaginaPorAbaGeraUmaPaginaPorAbaComTudo() throws Exception {
        parametros.planilhaModo = PlanilhaModo.PAGINA_POR_ABA;
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> {
            PlanilhasDeTeste.grade(p, "Alta", 3, 300, 0);
            PlanilhasDeTeste.grade(p, "Larga", 60, 5, 0);
        }));

        assertThat(c.resultado().paginas()).isEqualTo(2);
        assertThat(c.resultado().abasEmPaginaUnica()).isZero();
        assertThat(textoDaPagina(c.pdf(), 1)).contains(marca(3, 300));
        assertThat(textoDaPagina(c.pdf(), 2)).contains(marca(60, 5));
        assertThat(paisagem(c.pdf(), 0)).isFalse();
        assertThat(paisagem(c.pdf(), 1)).isTrue();
    }

    @Test
    void planilhaGrandeDemaisParaPaginaUnicaEPaginadaSemPerderLinhas() throws Exception {
        // R41: 30 mil linhas x 30 colunas largas ficam abaixo da escala mínima; a página única passaria do limite do
        // LibreOffice (~5 m), que cortava tudo depois das ~1.930 primeiras linhas sem erro.
        for (PlanilhaModo modo : PlanilhaModo.values()) {
            parametros.planilhaModo = modo;
            Path pedido = Files.createTempDirectory(pasta, "grande");
            Path origem = PlanilhasDeTeste.xlsxGrande(pedido.resolve("origem.xlsx"), 30_000, 30);
            Path destino = pedido.resolve("saida.pdf");

            ResultadoDaConversao resultado =
                    conversor.converter(origem, new TipoDetectado(TipoReal.PLANILHA, "xlsx"), destino, TITULO);

            String texto = texto(destino);
            assertThat(texto).as(modo.name()).contains("Linha 1 ", "Linha 15000 ", "Linha 30000 ", "Fim da planilha", TITULO);
            assertThat(resultado.abasEmPaginaUnica()).as(modo.name()).isZero();
            assertThat(resultado.paginas()).as(modo.name()).isGreaterThan(4);
        }
    }

    // ---- S3.1: cabeçalho com o nome do documento, sem rodapé do Calc, centralizada ----

    static final String TITULO = "Relatório de vazão — ação de outorga";

    @Test
    void umaAbaTemOTituloDoDocumentoNoCabecalhoSemNomeDaAbaNemPaginaDoCalc() throws Exception {
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> PlanilhasDeTeste.grade(p, "Planilha1", 4, 10, 0)),
                TITULO);

        String primeira = textoDaPagina(c.pdf(), 1).strip();
        assertThat(primeira).startsWith(TITULO);
        assertThat(texto(c.pdf())).doesNotContain("Planilha1").doesNotContain("Página 1").contains(marca(4, 10));
    }

    @Test
    void comMaisDeUmaAbaOCabecalhoLevaONomeDeCadaAbaEAOcultaSoEntraComOParametro() throws Exception {
        Consumer<Workbook> tres = p -> {
            PlanilhasDeTeste.grade(p, "Resumo", 3, 5, 0);
            PlanilhasDeTeste.grade(p, "Dados", 3, 5, 0);
            PlanilhasDeTeste.grade(p, "Oculta", 3, 5, 0);
            p.setSheetHidden(2, true);
        };
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(tres), TITULO);
        assertThat(c.resultado().paginas()).isEqualTo(2);
        assertThat(textoDaPagina(c.pdf(), 1).strip()).startsWith(TITULO + " — Resumo");
        assertThat(textoDaPagina(c.pdf(), 2).strip()).startsWith(TITULO + " — Dados");
        assertThat(texto(c.pdf())).doesNotContain("Oculta");

        parametros.abasOcultas = true;
        Convertido comOculta = converter("xlsx", PlanilhasDeTeste.xlsx(tres), TITULO);
        assertThat(comOculta.resultado().paginas()).isEqualTo(3);
        assertThat(textoDaPagina(comOculta.pdf(), 3).strip()).startsWith(TITULO + " — Oculta");
    }

    @Test
    void umaAbaVisivelComOutraOcultaForaNaoLevaONomeDaAba() throws Exception {
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> {
            PlanilhasDeTeste.grade(p, "Resumo", 3, 5, 0);
            PlanilhasDeTeste.grade(p, "Oculta", 3, 5, 0);
            p.setSheetHidden(1, true);
        }), TITULO);
        assertThat(texto(c.pdf())).contains(TITULO).doesNotContain("Resumo").doesNotContain("Oculta");
    }

    @Test
    void tabelaCentralizadaNaLarguraEmRetratoEPaisagemEstreitaELarga() throws Exception {
        // colunas x linhas: estreita retrato, estreita paisagem, larga paisagem, larga retrato
        int[][] casos = {{2, 100}, {2, 3}, {40, 30}, {40, 400}};
        for (int[] caso : casos) {
            Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> gradeComNumerosNaUltimaColuna(p, caso[0], caso[1])),
                    null);
            PDRectangle pagina;
            try (PDDocument documento = Loader.loadPDF(c.pdf().toFile())) {
                pagina = documento.getPage(0).getMediaBox();
            }
            float[] extremos = extremosHorizontais(c.pdf());
            float esquerda = extremos[0];
            float direita = pagina.getWidth() - extremos[1];
            assertThat(Math.abs(esquerda - direita)).as("%dx%d: esquerda %.1f, direita %.1f (página %.0f pt)",
                    caso[0], caso[1], esquerda, direita, pagina.getWidth()).isLessThan(4f);
        }
        assertThat(paisagemDoCaso(2, 3)).isTrue();
        assertThat(paisagemDoCaso(2, 100)).isFalse();
    }

    @Test
    void tituloHostilSaiComoTextoCortadoSemControleNemQuebrarOPdf() throws Exception {
        Consumer<Workbook> simples = p -> PlanilhasDeTeste.grade(p, "Planilha1", 3, 5, 0);
        String marcacao = "<b>Ação & {campo} $(TABLE) &P \u0007fim\nlinha";
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(simples), marcacao);
        String cabecalho = textoDaPagina(c.pdf(), 1).strip().lines().findFirst().orElse("");
        assertThat(cabecalho).isEqualTo("<b>Ação & {campo} $(TABLE) &P fim linha");

        String longo = "Documento com título muito longo ".repeat(20);
        Convertido cortado = converter("xlsx", PlanilhasDeTeste.xlsx(simples), longo);
        String linha = textoDaPagina(cortado.pdf(), 1).strip().lines().findFirst().orElse("");
        assertThat(linha).startsWith("Documento com título muito longo").endsWith("…");
        assertThat(linha.length()).isLessThan(longo.length());
        assertThat(cortado.resultado().paginas()).isEqualTo(1);

        Convertido vazio = converter("xlsx", PlanilhasDeTeste.xlsx(simples), "  \u0000 ");
        assertThat(textoDaPagina(vazio.pdf(), 1).strip()).startsWith(marca(1, 1)).doesNotContain("Planilha1");
    }

    @Test
    void modoPaginaPorAbaTambemTemCabecalhoESemRodape() throws Exception {
        parametros.planilhaModo = PlanilhaModo.PAGINA_POR_ABA;
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> PlanilhasDeTeste.grade(p, "Planilha1", 3, 300, 0)),
                TITULO);
        assertThat(c.resultado().paginas()).isEqualTo(1);
        String texto = textoDaPagina(c.pdf(), 1).strip();
        // A página tem a largura da tabela (3 colunas): o título é cortado com reticências para caber numa linha.
        String cabecalho = texto.lines().findFirst().orElse("");
        assertThat(cabecalho).endsWith("…");
        assertThat(TITULO).startsWith(cabecalho.substring(0, cabecalho.length() - 1));
        assertThat(texto).contains(marca(3, 300)).doesNotContain("Página 1").doesNotContain("Planilha1");
    }

    /** Coluna 1 com texto (à esquerda) e a última com números (à direita): os extremos do texto ficam simétricos. */
    private static void gradeComNumerosNaUltimaColuna(Workbook pasta, int colunas, int linhas) {
        Sheet aba = PlanilhasDeTeste.grade(pasta, "Dados", colunas, linhas, 0);
        for (int l = 0; l < linhas; l++) {
            aba.getRow(l).createCell(colunas - 1).setCellValue(1000 + l);
        }
    }

    private boolean paisagemDoCaso(int colunas, int linhas) throws Exception {
        Convertido c = converter("xlsx", PlanilhasDeTeste.xlsx(p -> gradeComNumerosNaUltimaColuna(p, colunas, linhas)), null);
        return paisagem(c.pdf(), 0);
    }

    /** Menor x e maior x (fim do glifo) do texto da primeira página, em pontos. */
    private static float[] extremosHorizontais(Path pdf) throws IOException {
        float[] extremos = {Float.MAX_VALUE, 0};
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper extrator = new PDFTextStripper() {
                @Override
                protected void writeString(String texto, List<org.apache.pdfbox.text.TextPosition> posicoes) {
                    for (org.apache.pdfbox.text.TextPosition p : posicoes) {
                        if (p.getUnicode().isBlank()) {
                            continue;
                        }
                        extremos[0] = Math.min(extremos[0], p.getXDirAdj());
                        extremos[1] = Math.max(extremos[1], p.getXDirAdj() + p.getWidthDirAdj());
                    }
                }
            };
            extrator.setStartPage(1);
            extrator.setEndPage(1);
            extrator.getText(documento);
        }
        return extremos;
    }

    // ---- apoio ----

    private Convertido converter(String extensao, byte[] conteudo, String titulo) throws IOException {
        Path pedido = Files.createTempDirectory(pasta, "pedido");
        Path origem = Files.write(pedido.resolve("origem." + extensao), conteudo);
        Path destino = pedido.resolve("saida.pdf");
        ResultadoDaConversao resultado =
                conversor.converter(origem, new TipoDetectado(TipoReal.PLANILHA, extensao), destino, titulo);
        return new Convertido(destino, resultado);
    }

    private Convertido converter(String extensao, byte[] conteudo) throws IOException {
        Path pedido = Files.createTempDirectory(pasta, "pedido");
        Path origem = Files.write(pedido.resolve("origem." + extensao), conteudo);
        Path destino = pedido.resolve("saida.pdf");
        ResultadoDaConversao resultado =
                conversor.converter(origem, new TipoDetectado(TipoReal.PLANILHA, extensao), destino);
        return new Convertido(destino, resultado);
    }

    private void assertFalha(String extensao, byte[] conteudo, Motivo esperado) throws IOException {
        Path pedido = Files.createTempDirectory(pasta, "pedido");
        Path origem = Files.write(pedido.resolve("origem." + extensao), conteudo);
        assertThatThrownBy(() -> conversor.converter(origem, new TipoDetectado(TipoReal.PLANILHA, extensao),
                pedido.resolve("saida.pdf")))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        f -> assertThat(f.motivo()).isEqualTo(esperado));
        try (var itens = Files.list(pedido)) {
            assertThat(itens.map(p -> p.getFileName().toString())).containsExactly("origem." + extensao);
        }
    }

    private static String texto(Path pdf) throws IOException {
        return ConversorLibreOfficeTest.texto(pdf);
    }

    private static String textoDaPagina(Path pdf, int pagina) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper extrator = new PDFTextStripper();
            extrator.setStartPage(pagina);
            extrator.setEndPage(pagina);
            return extrator.getText(documento);
        }
    }

    private static boolean paisagem(Path pdf, int indice) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            PDRectangle caixa = documento.getPage(indice).getMediaBox();
            int rotacao = documento.getPage(indice).getRotation() % 180;
            return rotacao == 0 ? caixa.getWidth() > caixa.getHeight() : caixa.getHeight() > caixa.getWidth();
        }
    }

    /** Marcas presentes, para comparar dois PDFs sem depender da ordem do texto extraído. */
    private static List<String> marcas(String texto, int colunas, int linhas) {
        List<String> presentes = new ArrayList<>();
        for (int l = 1; l <= linhas; l++) {
            for (int c = 1; c <= colunas; c++) {
                if (texto.contains(marca(c, l) + " ") || texto.endsWith(marca(c, l))) {
                    presentes.add(marca(c, l));
                }
            }
        }
        return presentes;
    }
}
