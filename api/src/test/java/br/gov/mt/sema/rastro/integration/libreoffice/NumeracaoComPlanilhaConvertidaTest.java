package br.gov.mt.sema.rastro.integration.libreoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.gov.mt.sema.rastro.dossie.application.PreparadorDePdf;
import br.gov.mt.sema.rastro.dossie.application.PreparadorDePdf.PdfPreparado;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie.DocumentoParaMontagem;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie.OpcoesDeMontagem;
import br.gov.mt.sema.rastro.dossie.domain.DadosDaCapa;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Dossiê com planilha convertida de verdade (S3.1, bloco 2, em conjunto com o bloco 1): cada página numerada tem
 * <b>uma única</b> numeração, a do dossiê, "Página X de Y" no canto inferior direito; nenhum "Página N" solto do
 * Calc. Exige o LibreOffice; sem ele, o teste é pulado com aviso (e falha no CI, como os demais de conversão).
 */
@SpringBootTest(properties = {"rastro.demo.senha=senha-de-teste", "rastro.datasource.siga.url=", "rastro.arquivos.url="})
class NumeracaoComPlanilhaConvertidaTest {

    private static final Pattern NUMERACAO = Pattern.compile("Página \\d+ de \\d+");
    private static final Pattern NUMERO_SOLTO = Pattern.compile("Página \\d+\\b(?! de \\d+)");

    @TempDir
    Path pasta;

    @Autowired
    PreparadorDePdf preparador;

    @Autowired
    MontadorDoDossie montador;

    @BeforeEach
    void exigir() {
        ConversorLibreOfficeTest.exigirSoffice();
    }

    @Test
    void cadaPaginaDaPlanilhaConvertidaTemSoANumeracaoDoDossieNoCantoInferiorDireito() throws Exception {
        Path larga = pasta.resolve("larga.xlsx");
        Files.write(larga, PlanilhasDeTeste.xlsx(pasta -> PlanilhasDeTeste.grade(pasta, "Dados", 40, 200, 0)));
        Path duasAbas = pasta.resolve("duas-abas.xlsx");
        Files.write(duasAbas, PlanilhasDeTeste.xlsx(pasta -> {
            PlanilhasDeTeste.grade(pasta, "Primeira", 6, 120, 0);
            PlanilhasDeTeste.grade(pasta, "Segunda", 6, 120, 0);
        }));

        List<DocumentoParaMontagem> documentos = new ArrayList<>();
        int ordem = 0;
        for (Path original : List.of(larga, duasAbas)) {
            Path pdf = pasta.resolve("convertido-" + ordem + ".pdf");
            PdfPreparado p = preparador.preparar(original, "xlsx", pdf, "Planilha de cálculo " + ordem);
            assertThat(p.convertidoDe()).isEqualTo("XLSX");
            documentos.add(new DocumentoParaMontagem("Planilha de cálculo " + ordem, "Documento do processo",
                    LocalDateTime.of(2025, 4, 1, 10, 0).plusDays(ordem), pdf, p.paginas(), p.convertidoDe(),
                    p.abasEmPaginaUnica()));
            ordem++;
        }
        Path dossie = pasta.resolve("dossie.pdf");

        int total = montador.montar(capa(), documentos, new OpcoesDeMontagem(true, true, true), dossie);

        try (PDDocument pdf = Loader.loadPDF(dossie.toFile())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(total);
            for (int pagina = 2; pagina <= total; pagina++) {
                String texto = texto(pdf, pagina);
                Matcher m = NUMERACAO.matcher(texto);
                int ocorrencias = 0;
                while (m.find()) {
                    ocorrencias++;
                }
                assertThat(ocorrencias).as("numerações na página %d", pagina).isEqualTo(1);
                assertThat(NUMERO_SOLTO.matcher(texto).find()).as("numeração do Calc na página %d", pagina).isFalse();
                assertThat(texto).contains("Página " + pagina + " de " + total);
                assertNoCantoInferiorDireito(pdf, pagina);
            }
        }
    }

    /** O fim do texto "Página X de Y" está na margem direita da página como ela é exibida, e à direita do centro. */
    private static void assertNoCantoInferiorDireito(PDDocument pdf, int pagina) throws IOException {
        List<TextPosition> caracteres = new ArrayList<>();
        PDFTextStripper extrator = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition texto) {
                caracteres.add(texto);
                super.processTextPosition(texto);
            }
        };
        extrator.setStartPage(pagina);
        extrator.setEndPage(pagina);
        String texto = extrator.getText(pdf);
        Matcher m = NUMERACAO.matcher(texto);
        assertThat(m.find()).isTrue();
        String numeracao = m.group();
        // Os últimos caracteres desenhados formam a numeração (o rodapé é a última coisa escrita na página).
        List<TextPosition> daNumeracao = caracteres.subList(caracteres.size() - numeracao.length(), caracteres.size());
        PDPage exibida = pdf.getPage(pagina - 1);
        boolean girada = exibida.getRotation() % 180 != 0;
        float larguraExibida = girada ? exibida.getCropBox().getHeight() : exibida.getCropBox().getWidth();
        float alturaExibida = girada ? exibida.getCropBox().getWidth() : exibida.getCropBox().getHeight();
        float inicio = Float.MAX_VALUE;
        float fim = 0;
        for (TextPosition c : daNumeracao) {
            inicio = Math.min(inicio, c.getXDirAdj());
            fim = Math.max(fim, c.getXDirAdj() + c.getWidthDirAdj());
        }
        assertThat(daNumeracao).as("negrito, página %d", pagina)
                .allSatisfy(c -> assertThat(c.getFont().getName()).containsIgnoringCase("Bold"));
        assertThat(larguraExibida - fim).as("margem direita, página %d", pagina).isBetween(26f, 30f);
        assertThat(inicio).as("à direita do centro, página %d", pagina).isGreaterThan(larguraExibida / 2);
        assertThat(alturaExibida - daNumeracao.get(0).getYDirAdj()).as("margem inferior, página %d", pagina)
                .isBetween(18f, 22f);
    }

    private static String texto(PDDocument pdf, int pagina) throws IOException {
        PDFTextStripper extrator = new PDFTextStripper();
        extrator.setStartPage(pagina);
        extrator.setEndPage(pagina);
        return extrator.getText(pdf);
    }

    private static DadosDaCapa capa() {
        return new DadosDaCapa("Processo", "2825/2025", "Associação Residencial Vale Verde", "12.345.678/0001-90",
                "Uso de Recursos Hídricos", "Em análise", LocalDateTime.of(2025, 3, 31, 10, 12), List.of("Consumo humano"),
                List.of("Captação"), LocalDateTime.of(2026, 10, 28, 10, 0), "Gestor Demo");
    }

}
