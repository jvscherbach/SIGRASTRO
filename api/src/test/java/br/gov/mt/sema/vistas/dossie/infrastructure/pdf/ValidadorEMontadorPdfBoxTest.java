package br.gov.mt.sema.vistas.dossie.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie.DocumentoParaMontagem;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie.Inventario;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie.OpcoesDeMontagem;
import br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa;
import br.gov.mt.sema.vistas.dossie.domain.DocumentoNaoIncluido;
import br.gov.mt.sema.vistas.testsupport.PdfsDeTeste;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidadorEMontadorPdfBoxTest {

    private static final OpcoesDeMontagem TUDO = new OpcoesDeMontagem(true, true, true);
    private static final LocalDateTime D = LocalDateTime.of(2025, 4, 2, 9, 0);

    @TempDir
    Path pasta;

    private final ValidadorPdfBox validador = new ValidadorPdfBox();
    private final MontadorPdfBox montador = new MontadorPdfBox();

    private static DadosDaCapa capa() {
        return new DadosDaCapa("Processo", "2825/2025", "Associação Residencial Vale Verde", "12.345.678/0001-90",
                "Uso de Recursos Hídricos", "Em análise", LocalDateTime.of(2025, 3, 31, 10, 12),
                List.of("Consumo humano", "Irrigação"), List.of("Abastecimento público - Captação"),
                LocalDateTime.of(2026, 10, 21, 14, 30), "Gestor Demo");
    }

    private DocumentoParaMontagem doc(String titulo, int paginas, PDRectangle tamanho, int rotacao) {
        Path arquivo = PdfsDeTeste.criar(pasta.resolve(titulo.replaceAll("\\W", "") + paginas + rotacao + ".pdf"),
                paginas, tamanho, rotacao, titulo);
        return new DocumentoParaMontagem(titulo, "Documento do processo", D, arquivo, paginas);
    }

    private static String texto(PDDocument documento, int paginaInicial, int paginaFinal) throws IOException {
        PDFTextStripper extrator = new PDFTextStripper();
        extrator.setStartPage(paginaInicial);
        extrator.setEndPage(paginaFinal);
        return extrator.getText(documento);
    }

    // --- validação

    @Test
    void pdfValidoDevolveONumeroDePaginas() {
        assertThat(validador.validar(PdfsDeTeste.criarA4(pasta.resolve("a.pdf"), 3, "x"))).isEqualTo(3);
    }

    @Test
    void assinaturaDentroDosPrimeiros1024BytesBasta() throws IOException {
        Path normal = PdfsDeTeste.criarA4(pasta.resolve("a.pdf"), 1, "x");
        Path comLixoAntes = pasta.resolve("b.pdf");
        Files.write(comLixoAntes, concat("lixo-antes\n".getBytes(), Files.readAllBytes(normal)));

        assertThat(validador.validar(comLixoAntes)).isEqualTo(1);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    @Test
    void pdfComSenhaDeAberturaEFalhaDoDocumento() {
        assertThatThrownBy(() -> validador.validar(PdfsDeTeste.comSenha(pasta.resolve("s.pdf"))))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("o documento está protegido por senha");
    }

    @Test
    void pdfCorrompidoEFalhaDoDocumento() {
        assertThatThrownBy(() -> validador.validar(PdfsDeTeste.corrompido(pasta.resolve("c.pdf"))))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("o arquivo está corrompido");
    }

    @Test
    void pdfSemPaginasEFalhaDoDocumento() {
        assertThatThrownBy(() -> validador.validar(PdfsDeTeste.semPaginas(pasta.resolve("v.pdf"))))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("o arquivo não tem páginas");
    }

    @Test
    void conteudoQueNaoEPdfEFalhaDoDocumento() {
        assertThatThrownBy(() -> validador.validar(PdfsDeTeste.html(pasta.resolve("h.pdf"))))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("o arquivo não é um PDF (tipo detectado: HTML)");
    }

    @Test
    void tipoDetectadoPelaAssinaturaDoConteudo() throws IOException {
        Path zip = pasta.resolve("a.pdf");
        java.nio.file.Files.write(zip, new byte[] {0x50, 0x4B, 0x03, 0x04, 0});
        Path png = pasta.resolve("b.pdf");
        java.nio.file.Files.write(png, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});

        assertThatThrownBy(() -> validador.validar(zip)).hasMessageContaining("ZIP ou documento do Office");
        assertThatThrownBy(() -> validador.validar(png)).hasMessageContaining("imagem PNG");
    }

    // --- montagem

    @Test
    void preservaTamanhoRotacaoEQuantidadeDePaginasDeCadaDocumento() throws IOException {
        List<DocumentoParaMontagem> docs = List.of(
                doc("Oficio", 2, PDRectangle.A4, 0),
                doc("Planta", 1, new PDRectangle(1190f, 842f), 90),
                doc("Carta", 3, PDRectangle.LETTER, 180),
                doc("Anexo", 1, new PDRectangle(300f, 500f), 270));
        Path saida = pasta.resolve("dossie.pdf");

        int total = montador.montar(capa(), docs, TUDO, saida);

        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            assertThat(total).isEqualTo(pdf.getNumberOfPages()).isEqualTo(1 + 1 + 7);
            // páginas dos documentos começam depois da capa e de 1 página de sumário
            int[][] esperado = {{0, 595, 842}, {0, 595, 842}, {90, 1190, 842}, {180, 612, 792}, {180, 612, 792}, {180, 612, 792}, {270, 300, 500}};
            for (int i = 0; i < esperado.length; i++) {
                PDPage pagina = pdf.getPage(2 + i);
                assertThat(pagina.getRotation()).as("rotação da página %d", i).isEqualTo(esperado[i][0]);
                assertThat(Math.round(pagina.getMediaBox().getWidth())).as("largura %d", i).isEqualTo(esperado[i][1]);
                assertThat(Math.round(pagina.getMediaBox().getHeight())).as("altura %d", i).isEqualTo(esperado[i][2]);
            }
            assertThat(texto(pdf, 3, 3)).contains("Oficio - pagina 1");
            // a última página está girada em 270°: o extrator quebra o texto vertical, então confere-se o rodapé
            assertThat(texto(pdf, 9, 9)).contains("Página 9 de 9");
        }
    }

    @Test
    void capaTrazOsDadosDoRequerimentoComAcentosEFonteSegura() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");
        DadosDaCapa comCaracteresForaDoWinAnsi = new DadosDaCapa("Requerimento", "3331/2025", "Associação Nº 1 — Ωmega 日本",
                "12.345.678/0001-90", "Uso de Recursos Hídricos", "Em análise", LocalDateTime.of(2025, 3, 31, 10, 12),
                List.of("Consumo humano", "Irrigação"), List.of("Captação º ã é ç"), LocalDateTime.of(2026, 10, 21, 14, 30),
                "Gestor Demo");

        montador.montar(comCaracteresForaDoWinAnsi, List.of(doc("Oficio", 1, PDRectangle.A4, 0)), TUDO, saida);

        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            String capaTexto = texto(pdf, 1, 1);
            assertThat(capaTexto).contains("Dossiê do Requerimento 3331/2025", "Associação", "Hídricos", "Captação º ã é ç",
                    "12.345.678/0001-90", "31/03/2025", "Consumo humano", "Irrigação", "Documentos neste dossiê",
                    "Gerado em 21/10/2026 às 14:30 por Gestor Demo");
            assertThat(capaTexto).contains("?"); // Ω e os ideogramas não cabem no WinAnsi
        }
    }

    @Test
    void sumarioListaCadaDocumentoComDataTituloOrigemEPaginaInicial() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");
        montador.montar(capa(), List.of(doc("Oficio", 2, PDRectangle.A4, 0), doc("Planta", 3, PDRectangle.A4, 0),
                new DocumentoParaMontagem("Sem data", "Juntada", null, PdfsDeTeste.criarA4(pasta.resolve("sd.pdf"), 1, "sd"), 1)),
                TUDO, saida);

        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            String sumario = texto(pdf, 2, 2);
            assertThat(sumario).contains("Sumário");
            // capa = 1, sumário = 2, documentos começam na 3: Oficio (3-4), Planta (5-7), Sem data (8)
            assertThat(sumario).containsPattern("02/04/2025\\s+Oficio\\s+Documento do processo\\s+3");
            assertThat(sumario).containsPattern("02/04/2025\\s+Planta\\s+Documento do processo\\s+5");
            assertThat(sumario).containsPattern("sem data\\s+Sem data\\s+Juntada\\s+8");
            assertThat(sumario).contains("aparecem ao final, por origem e id");
        }
    }

    @Test
    void semDocumentoSemDataNaoHaNotaNoSumario() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");
        montador.montar(capa(), List.of(doc("Oficio", 1, PDRectangle.A4, 0)), TUDO, saida);

        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            assertThat(texto(pdf, 2, 2)).doesNotContain("sem data").doesNotContain("aparecem ao final");
        }
    }

    @Test
    void horariosDeBordaNaoDeslocamODiaNoSumarioComJvmEmUtc() throws IOException {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            Path um = PdfsDeTeste.criarA4(pasta.resolve("um.pdf"), 1, "um");
            Path saida = pasta.resolve("dossie.pdf");
            montador.montar(capa(), List.of(
                    new DocumentoParaMontagem("Noite", "Juntada", LocalDateTime.of(2025, 4, 1, 23, 30), um, 1),
                    new DocumentoParaMontagem("Madrugada", "Juntada", LocalDateTime.of(2025, 4, 2, 0, 30), um, 1)),
                    TUDO, saida);

            try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
                String sumario = texto(pdf, 2, 2);
                assertThat(sumario).containsPattern("01/04/2025\\s+Noite");
                assertThat(sumario).containsPattern("02/04/2025\\s+Madrugada");
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void sumarioTemPaginacaoPropriaEDeterministica() throws IOException {
        List<DocumentoParaMontagem> docs = new ArrayList<>();
        Path um = PdfsDeTeste.criarA4(pasta.resolve("um.pdf"), 1, "um");
        for (int i = 1; i <= 80; i++) {
            docs.add(new DocumentoParaMontagem("Documento " + i, "Juntada", D, um, 1));
        }
        Path saida = pasta.resolve("dossie.pdf");

        int total = montador.montar(capa(), docs, TUDO, saida);

        // 80 linhas a 38 por página = 3 páginas de sumário; documentos começam na página 1 + 3 + 1 = 5.
        assertThat(total).isEqualTo(1 + 3 + 80);
        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            assertThat(texto(pdf, 2, 2)).contains("Documento 1 ").containsPattern("Documento 1\\s+Juntada\\s+5\\b")
                    .doesNotContain("Documento 39 ");
            assertThat(texto(pdf, 3, 3)).containsPattern("Documento 39\\s+Juntada\\s+43\\b");
            assertThat(texto(pdf, 4, 4)).containsPattern("Documento 80\\s+Juntada\\s+84\\b");
        }
    }

    @Test
    void cadaLinhaDoSumarioTemUmLinkParaOPrimeiroPaginaDoDocumento() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");
        montador.montar(capa(), List.of(doc("Oficio", 2, PDRectangle.A4, 0), doc("Planta", 1, PDRectangle.A4, 0)), TUDO, saida);

        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            List<PDAnnotationLink> links = pdf.getPage(1).getAnnotations().stream()
                    .filter(PDAnnotationLink.class::isInstance).map(PDAnnotationLink.class::cast).toList();
            assertThat(links).hasSize(2);
        }
    }

    // --- documentos não incluídos (S2.2)

    private static Inventario inventario(int listados, DocumentoNaoIncluido... fora) {
        return new Inventario(listados, List.of(fora));
    }

    private static final DocumentoNaoIncluido REMOVIDO = new DocumentoNaoIncluido(1, "Complementação", "900118",
            "Resposta da complementação", LocalDateTime.of(2025, 4, 1, 8, 0),
            DocumentoNaoIncluido.removido("tempo limite excedido ao obter o arquivo"));
    private static final DocumentoNaoIncluido INDISPONIVEL = new DocumentoNaoIncluido(3, "Imóvel", "31", "Matrícula",
            LocalDateTime.of(2025, 4, 3, 8, 0), DocumentoNaoIncluido.INDISPONIVEL);
    private static final DocumentoNaoIncluido NAO_PDF = new DocumentoNaoIncluido(4, "Documento do processo", "804330",
            "Cálculo de vazão", null, DocumentoNaoIncluido.NAO_PDF);

    private List<String> marcadores(PDDocument pdf, List<Integer> paginas) throws IOException {
        List<String> titulos = new ArrayList<>();
        for (PDOutlineItem item : pdf.getDocumentCatalog().getDocumentOutline().children()) {
            titulos.add(item.getTitle());
            paginas.add(pdf.getPages().indexOf(item.findDestinationPage(pdf)));
        }
        return titulos;
    }

    @Test
    void secaoDeNaoIncluidosVemLogoAposOSumarioComOrigemIdTituloDataEMotivo() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");

        int total = montador.montar(capa(), List.of(doc("Oficio", 2, PDRectangle.A4, 0), doc("Planta", 1, PDRectangle.A4, 0)),
                inventario(5, REMOVIDO, INDISPONIVEL, NAO_PDF), TUDO, saida);

        // capa (1), sumário (2), não incluídos (3), Oficio (4-5), Planta (6)
        assertThat(total).isEqualTo(6);
        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            assertThat(texto(pdf, 1, 1)).contains("2 documentos incluídos de 5 listados");
            assertThat(texto(pdf, 2, 2)).containsPattern("Oficio\\s+Documento do processo\\s+4")
                    .containsPattern("Planta\\s+Documento do processo\\s+6");
            String secao = texto(pdf, 3, 3);
            assertThat(secao).contains("Documentos não incluídos");
            assertThat(secao).containsPattern("01/04/2025\\s+Resposta da complementação\\s+Complementação\\s+900118");
            assertThat(secao).contains("Motivo: removido pelo usuário após falha: tempo limite excedido ao obter o arquivo");
            assertThat(secao).containsPattern("03/04/2025\\s+Matrícula\\s+Imóvel\\s+31");
            assertThat(secao).contains("Motivo: indisponível (chave do arquivo inválida)");
            assertThat(secao).containsPattern("sem data\\s+Cálculo de vazão");
            assertThat(secao).contains("Motivo: não é PDF: aguardando conversão");
            assertThat(secao.indexOf("Resposta da complementação")).isLessThan(secao.indexOf("Matrícula"));
            assertThat(texto(pdf, 4, 4)).contains("Oficio");

            List<Integer> paginas = new ArrayList<>();
            assertThat(marcadores(pdf, paginas)).containsExactly("Capa", "Sumário", "Documentos não incluídos", "Oficio",
                    "Planta");
            assertThat(paginas).containsExactly(0, 1, 2, 3, 5);
            assertThat(texto(pdf, 3, 3)).contains("Página 3 de 6");
        }
    }

    @Test
    void semNaoIncluidosASecaoEOmitidaEACapaMostraTodosOsListados() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");

        int total = montador.montar(capa(), List.of(doc("Oficio", 1, PDRectangle.A4, 0)), inventario(1), TUDO, saida);

        assertThat(total).isEqualTo(3);
        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            assertThat(texto(pdf, 1, 3)).doesNotContain("Documentos não incluídos");
            assertThat(texto(pdf, 1, 1)).contains("1 documento incluído de 1 listado");
            List<Integer> paginas = new ArrayList<>();
            assertThat(marcadores(pdf, paginas)).containsExactly("Capa", "Sumário", "Oficio");
            assertThat(paginas).containsExactly(0, 1, 2);
        }
    }

    @Test
    void secaoTemPaginacaoPropriaEDeterministica() throws IOException {
        List<DocumentoNaoIncluido> fora = new ArrayList<>();
        for (int i = 1; i <= MontagemPdfBox.ITENS_POR_PAGINA_DOS_NAO_INCLUIDOS + 1; i++) {
            fora.add(new DocumentoNaoIncluido(i, "Juntada", String.valueOf(i), "Fora " + i, null,
                    DocumentoNaoIncluido.NAO_SELECIONADO));
        }
        Path saida = pasta.resolve("dossie.pdf");

        int total = montador.montar(capa(), List.of(doc("Oficio", 1, PDRectangle.A4, 0)),
                new Inventario(1 + fora.size(), fora), TUDO, saida);

        // capa, sumário, 2 páginas da seção (22 + 1), Oficio na 5
        assertThat(total).isEqualTo(5);
        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            assertThat(texto(pdf, 2, 2)).containsPattern("Oficio\\s+Documento do processo\\s+5");
            assertThat(texto(pdf, 3, 3)).contains("Fora 22 ").doesNotContain("Fora 23");
            assertThat(texto(pdf, 4, 4)).contains("Fora 23").contains("Documentos não incluídos");
            assertThat(texto(pdf, 1, 1)).contains("1 documento incluído de 24 listados");
            List<Integer> paginas = new ArrayList<>();
            marcadores(pdf, paginas);
            assertThat(paginas).containsExactly(0, 1, 2, 4);
        }
    }

    @Test
    void semSumarioASecaoVemLogoAposACapaEContinuaObrigatoria() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");

        int total = montador.montar(capa(), List.of(doc("Oficio", 1, PDRectangle.A4, 0)), inventario(2, INDISPONIVEL),
                new OpcoesDeMontagem(false, true, true), saida);

        assertThat(total).isEqualTo(3);
        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            assertThat(texto(pdf, 2, 2)).contains("Documentos não incluídos").doesNotContain("Sumário");
            List<Integer> paginas = new ArrayList<>();
            assertThat(marcadores(pdf, paginas)).containsExactly("Capa", "Documentos não incluídos", "Oficio");
            assertThat(paginas).containsExactly(0, 1, 2);
        }
    }

    @Test
    void convertidoFicaIdentificadoNoSumarioNosMarcadoresENaCapaSemMudarAPaginacao() throws IOException {
        DocumentoParaMontagem pdf = doc("Requerimento", 1, PDRectangle.A4, 0);
        DocumentoParaMontagem base = doc("Planilha de custos com um título bem longo que precisa ser cortado no sumário",
                2, PDRectangle.A4, 0);
        DocumentoParaMontagem planilha = new DocumentoParaMontagem(base.titulo(), base.origem(), base.data(),
                base.arquivo(), base.paginas(), "XLSX", 1);
        DocumentoParaMontagem imagemBase = doc("Foto", 1, PDRectangle.A4, 0);
        DocumentoParaMontagem imagem = new DocumentoParaMontagem("Foto", imagemBase.origem(), imagemBase.data(),
                imagemBase.arquivo(), 1, "PNG", 0);
        Path destino = pasta.resolve("dossie.pdf");

        int paginas = montador.montar(capa(), List.of(pdf, planilha, imagem), inventario(3), TUDO, destino);

        assertThat(paginas).isEqualTo(1 + 1 + 1 + 2 + 1);
        try (PDDocument dossie = Loader.loadPDF(destino.toFile())) {
            assertThat(texto(dossie, 1, 1)).contains("3 documentos incluídos de 3 listados; 2 convertidos para PDF");
            String sumario = texto(dossie, 2, 2).replaceAll("\\s+", " ");
            assertThat(sumario).contains("(convertido de XLSX*)").contains("Foto (convertido de PNG)")
                    .contains("* Planilha com aba(s) em página única")
                    .doesNotContain("Requerimento (convertido");
            // Página inicial de cada documento: 3, 4 e 6 (capa, sumário, depois os documentos).
            assertThat(sumario).containsSubsequence("Requerimento", "3", "Planilha", "4", "Foto", "6");
            List<Integer> paginasDosMarcadores = new ArrayList<>();
            assertThat(marcadores(dossie, paginasDosMarcadores)).containsExactly("Capa", "Sumário", "Requerimento",
                    planilha.titulo() + " (convertido de XLSX)", "Foto (convertido de PNG)");
        }
    }

    @Test
    void textoDaCapaComConvertidos() {
        assertThat(MontagemPdfBox.incluidosDeListados(8, 10, 0)).isEqualTo("8 documentos incluídos de 10 listados");
        assertThat(MontagemPdfBox.incluidosDeListados(8, 10, 1))
                .isEqualTo("8 documentos incluídos de 10 listados; 1 convertido para PDF");
        assertThat(MontagemPdfBox.incluidosDeListados(8, 10, 3))
                .isEqualTo("8 documentos incluídos de 10 listados; 3 convertidos para PDF");
    }

    @Test
    void textoDaCapaParaIncluidosEListados() {
        assertThat(MontagemPdfBox.incluidosDeListados(8, 10)).isEqualTo("8 documentos incluídos de 10 listados");
        assertThat(MontagemPdfBox.incluidosDeListados(1, 1)).isEqualTo("1 documento incluído de 1 listado");
        assertThat(MontagemPdfBox.incluidosDeListados(3, 0)).as("sem inventário").isEqualTo("3 documentos incluídos de 3 listados");
    }

    @Test
    void marcadoresApontamParaCapaSumarioECadaDocumento() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");
        montador.montar(capa(), List.of(doc("Oficio", 2, PDRectangle.A4, 0), doc("Planta", 1, PDRectangle.A4, 0)), TUDO, saida);

        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            PDDocumentOutline contorno = pdf.getDocumentCatalog().getDocumentOutline();
            assertThat(contorno).isNotNull();
            List<String> titulos = new ArrayList<>();
            List<Integer> paginas = new ArrayList<>();
            for (PDOutlineItem item : contorno.children()) {
                titulos.add(item.getTitle());
                paginas.add(pdf.getPages().indexOf(item.findDestinationPage(pdf)));
            }
            assertThat(titulos).containsExactly("Capa", "Sumário", "Oficio", "Planta");
            assertThat(paginas).containsExactly(0, 1, 2, 4);
        }
    }

    @Test
    void numeracaoPaginaXDeYAPartirDaPrimeiraPaginaAposACapa() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");
        montador.montar(capa(), List.of(doc("Oficio", 2, PDRectangle.A4, 0)), TUDO, saida);

        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            assertThat(texto(pdf, 1, 1)).doesNotContain("Página 1");
            assertThat(texto(pdf, 2, 2)).contains("Página 2 de 4");
            assertThat(texto(pdf, 3, 3)).contains("Página 3 de 4");
            assertThat(texto(pdf, 4, 4)).contains("Página 4 de 4");
        }
    }

    @Test
    void numeroFicaNoCantoInferiorDireitoComoAPaginaEExibidaEmCadaRotacao() throws IOException {
        for (int rotacao : new int[] {0, 90, 180, 270}) {
            Path saida = pasta.resolve("dossie-" + rotacao + ".pdf");
            Path emBranco = PdfsDeTeste.criarEmBranco(pasta.resolve("branco-" + rotacao + ".pdf"), 1,
                    new PDRectangle(600f, 400f), rotacao);
            montador.montar(capa(), List.of(new DocumentoParaMontagem("Girada", "Juntada", D, emBranco, 1)),
                    new OpcoesDeMontagem(false, false, true), saida);

            try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
                // páginas: 0 = capa, 1 = documento (sem sumário). Renderiza como o leitor mostra, rotação incluída.
                BufferedImage imagem = new PDFRenderer(pdf).renderImage(1, 1f);
                int largura = imagem.getWidth();
                int altura = imagem.getHeight();
                int[] tudo = caixaDosPixelsEscuros(imagem, 190);
                assertThat(tudo).as("número visível com rotação %d", rotacao).isNotNull();
                // Página em branco: tudo o que há de escuro é o número, e ele tem de estar no canto de baixo à direita.
                assertThat(tudo[1]).as("topo do número, rotação %d", rotacao).isGreaterThan(altura - 45);
                assertThat(tudo[3]).as("base do número, rotação %d", rotacao).isLessThan(altura);
                assertThat(tudo[0]).as("início do número, à direita do centro, rotação %d", rotacao).isGreaterThan(largura / 2);
                assertThat(Math.abs(largura - tudo[2] - MontagemPdfBox.MARGEM_DIREITA_DO_RODAPE))
                        .as("borda direita do número na margem direita, rotação %d", rotacao).isLessThan(4);
            }
        }
    }

    /** Posição do número de página como o leitor a vê: a distância até as bordas da página exibida. */
    private record PosicaoDoNumero(float inicio, float fim, float baseAteABordaInferior, float larguraDaPagina) {

        float folgaDireita() {
            return larguraDaPagina - fim;
        }
    }

    /** Posições (já ajustadas à rotação) dos caracteres da página, que no PDF em branco são só os do número. */
    private static PosicaoDoNumero posicaoDoNumero(PDDocument pdf, int pagina) throws IOException {
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
        extrator.getText(pdf);
        assertThat(caracteres).as("texto na página %d", pagina).isNotEmpty();
        float inicio = Float.MAX_VALUE;
        float fim = 0;
        for (TextPosition c : caracteres) {
            inicio = Math.min(inicio, c.getXDirAdj());
            fim = Math.max(fim, c.getXDirAdj() + c.getWidthDirAdj());
        }
        // Tamanho da página exibida: com rotação de 90 ou 270 graus largura e altura da caixa de recorte se trocam.
        PDPage exibida = pdf.getPage(pagina - 1);
        boolean girada = exibida.getRotation() % 180 != 0;
        float larguraExibida = girada ? exibida.getCropBox().getHeight() : exibida.getCropBox().getWidth();
        float alturaExibida = girada ? exibida.getCropBox().getWidth() : exibida.getCropBox().getHeight();
        return new PosicaoDoNumero(inicio, fim, alturaExibida - caracteres.get(0).getYDirAdj(), larguraExibida);
    }

    @Test
    void numeroAlinhadoADireitaNaMargemEmPaginasRetratoEPaisagem() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");
        Path retrato = PdfsDeTeste.criarEmBranco(pasta.resolve("retrato.pdf"), 1, PDRectangle.A4, 0);
        Path paisagem = PdfsDeTeste.criarEmBranco(pasta.resolve("paisagem.pdf"), 1,
                new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()), 0);
        montador.montar(capa(), List.of(new DocumentoParaMontagem("Retrato", "Juntada", D, retrato, 1),
                new DocumentoParaMontagem("Paisagem", "Juntada", D.plusDays(1), paisagem, 1)),
                new OpcoesDeMontagem(false, false, true), saida);

        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            for (int pagina : new int[] {2, 3}) {
                PosicaoDoNumero p = posicaoDoNumero(pdf, pagina);
                assertThat(p.folgaDireita()).as("margem direita, página %d", pagina)
                        .isCloseTo(MontagemPdfBox.MARGEM_DIREITA_DO_RODAPE, org.assertj.core.data.Offset.offset(1.5f));
                assertThat(p.baseAteABordaInferior()).as("margem inferior, página %d", pagina)
                        .isCloseTo(20f, org.assertj.core.data.Offset.offset(1.5f));
                assertThat(p.inicio()).as("à direita do centro, página %d", pagina).isGreaterThan(p.larguraDaPagina() / 2);
            }
        }
    }

    @Test
    void numeroSegueOCantoComTamanhosDePaginaEmRotacoesDiferentesNoMesmoDossie() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");
        List<DocumentoParaMontagem> documentos = new ArrayList<>();
        PDRectangle[] tamanhos = {PDRectangle.A4, new PDRectangle(200f, 600f), new PDRectangle(3000f, 2000f)};
        int[] rotacoes = {0, 90, 180, 270};
        int numero = 0;
        for (PDRectangle tamanho : tamanhos) {
            for (int rotacao : rotacoes) {
                Path arquivo = PdfsDeTeste.criarEmBranco(pasta.resolve("p" + (numero++) + ".pdf"), 1, tamanho, rotacao);
                documentos.add(new DocumentoParaMontagem("Doc " + numero, "Juntada", D.plusDays(numero), arquivo, 1));
            }
        }

        int total = montador.montar(capa(), documentos, new OpcoesDeMontagem(false, false, true), saida);

        assertThat(total).isEqualTo(1 + documentos.size());
        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            for (int pagina = 2; pagina <= total; pagina++) {
                PosicaoDoNumero p = posicaoDoNumero(pdf, pagina);
                assertThat(p.folgaDireita()).as("margem direita, página %d", pagina)
                        .isCloseTo(MontagemPdfBox.MARGEM_DIREITA_DO_RODAPE, org.assertj.core.data.Offset.offset(1.5f));
                assertThat(p.baseAteABordaInferior()).as("margem inferior, página %d", pagina)
                        .isCloseTo(20f, org.assertj.core.data.Offset.offset(1.5f));
                assertThat(p.inicio()).as("à direita do centro, página %d", pagina).isGreaterThan(p.larguraDaPagina() / 2);
                assertThat(texto(pdf, pagina, pagina)).contains("Página " + pagina + " de " + total);
            }
            assertThat(texto(pdf, 1, 1)).as("a capa não é numerada").doesNotContain("Página 1");
        }
    }

    @Test
    void cadaPaginaNumeradaTemUmaUnicaNumeracaoESemFundoBrancoQueEscondaOConteudo() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");
        montador.montar(capa(), List.of(doc("Oficio", 3, PDRectangle.A4, 0), doc("Planta", 2, PDRectangle.A4, 0)), TUDO, saida);

        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            int total = pdf.getNumberOfPages();
            java.util.regex.Pattern numeracao = java.util.regex.Pattern.compile("Página \\d+ de \\d+");
            java.util.regex.Pattern pagina1 = java.util.regex.Pattern.compile("Página \\d+\\b(?! de \\d+)");
            for (int pagina = 2; pagina <= total; pagina++) {
                String texto = texto(pdf, pagina, pagina);
                java.util.regex.Matcher m = numeracao.matcher(texto);
                int ocorrencias = 0;
                while (m.find()) {
                    ocorrencias++;
                }
                assertThat(ocorrencias).as("numerações na página %d", pagina).isEqualTo(1);
                assertThat(pagina1.matcher(texto).find()).as("numeração solta na página %d", pagina).isFalse();
            }
            // O texto do documento (o título que o PDF de teste desenha) continua na página, sob o fundo do número.
            assertThat(texto(pdf, 4, 4)).contains("Oficio");
        }
    }

    /** Menor retângulo [x0, y0, x1, y1] com pixels escuros; nulo se não houver. */
    private static int[] caixaDosPixelsEscuros(BufferedImage imagem, int limite) {
        return caixaDosPixelsEscuros(imagem, limite, 0, imagem.getHeight());
    }

    private static int[] caixaDosPixelsEscuros(BufferedImage imagem, int limite, int deY, int ateY) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = -1;
        int y1 = -1;
        for (int y = Math.max(0, deY); y < Math.min(ateY, imagem.getHeight()); y++) {
            for (int x = 0; x < imagem.getWidth(); x++) {
                int rgb = imagem.getRGB(x, y);
                int cinza = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                if (cinza < limite) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        return x1 < 0 ? null : new int[] {x0, y0, x1, y1};
    }

    @Test
    void sumarioMarcadoresENumeracaoRespeitamOsParametros() throws IOException {
        Path saida = pasta.resolve("dossie.pdf");

        int total = montador.montar(capa(), List.of(doc("Oficio", 2, PDRectangle.A4, 0)),
                new OpcoesDeMontagem(false, false, false), saida);

        assertThat(total).isEqualTo(1 + 2);
        try (PDDocument pdf = Loader.loadPDF(saida.toFile())) {
            assertThat(pdf.getDocumentCatalog().getDocumentOutline()).isNull();
            assertThat(texto(pdf, 1, 3)).doesNotContain("Sumário").doesNotContain("Página 2 de");
        }
    }

    @Test
    void documentoQueMudouDeTamanhoEntreValidacaoEMontagemEhErro() {
        DocumentoParaMontagem mentiroso = new DocumentoParaMontagem("Oficio", "Juntada", D,
                PdfsDeTeste.criarA4(pasta.resolve("m.pdf"), 2, "m"), 5);

        assertThatThrownBy(() -> montador.montar(capa(), List.of(mentiroso), TUDO, pasta.resolve("dossie.pdf")))
                .isInstanceOf(IllegalStateException.class);
    }
}
