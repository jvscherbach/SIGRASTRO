package br.gov.mt.sema.rastro.integration.libreoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

import br.gov.mt.sema.rastro.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.rastro.dossie.application.PreparadorDePdf;
import br.gov.mt.sema.rastro.dossie.application.PreparadorDePdf.PdfPreparado;
import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie.DocumentoParaMontagem;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie.OpcoesDeMontagem;
import br.gov.mt.sema.rastro.dossie.domain.DadosDaCapa;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Medição de desempenho da conversão (S3, bloco 9), com os parâmetros reais da aplicação (pool de 2 instâncias do
 * LibreOffice, timeout de 300 s) e arquivos sintéticos gerados aqui: XLSX e XLS de 30 mil linhas x 30 colunas, DOCX de
 * 200 páginas, JPG de 20 MP e um dossiê misto de 20 documentos (PDFs de ~50 MB, planilhas, textos e imagens). Registra
 * o tempo, o pico de heap da aplicação, a memória residente do {@code soffice}, o pico de disco e o tamanho do PDF
 * final, em {@code target/desempenho-conversao.txt}. Não corrige nada: passando dos limites (heap acima de 70%,
 * conversão perto do timeout), o teste lista o alerta e falha só no fim, depois de gravar todos os números.
 *
 * <p>Exige o LibreOffice e gera ~1 GB em disco; desligado por padrão (docs/10). Para rodar, com o heap de um container
 * de 1 GB (MaxRAMPercentage=75):
 *
 * <pre>./mvnw -Dtestes.excluidos= -Dgroups=desempenho -Dtest=DesempenhoDaConversaoTest -DargLine=-Xmx768m test</pre>
 */
@Tag("desempenho")
@SpringBootTest(properties = {"rastro.demo.senha=senha-de-teste", "rastro.datasource.siga.url=", "rastro.arquivos.url="})
class DesempenhoDaConversaoTest {

    private static final long MB = 1024L * 1024L;
    private static final int LINHAS = 30_000;
    /**
     * Marca da última linha, sozinha na coluna A (a vizinha vazia): sai inteira no PDF. "Linha 30000" não serve, porque
     * na largura padrão da coluna o Calc corta o texto na borda quando a célula ao lado tem conteúdo (R41).
     */
    private static final String FIM = "Fim da planilha";
    private static final int COLUNAS = 30;
    private static final int PAGINAS_DO_DOCX = 200;
    private static final int PDFS_NO_MISTO = 6;

    @TempDir
    Path pasta;

    @Autowired
    PreparadorDePdf preparador;

    @Autowired
    MontadorDoDossie montador;

    @Autowired
    ParametrosDaConversao parametros;

    private final List<String> linhasDoRelatorio = new ArrayList<>();
    private final List<String> alertas = new ArrayList<>();

    @Test
    void medirConversaoPorTipoENoDossieMisto() throws Exception {
        ConversorLibreOfficeTest.exigirSoffice();
        registrar("Heap máximo da JVM: %d MB; conversão: timeout %d s, %d processo(s), %d tarefas por processo, modo %s."
                .formatted(Runtime.getRuntime().maxMemory() / MB, parametros.timeoutSegundos(), parametros.processos(),
                        parametros.tarefasPorProcesso(), parametros.planilhaModo()));

        Path xlsx = gerar("planilha-30k.xlsx", () -> xlsxGrande(pasta.resolve("planilha-30k.xlsx")));
        medir("XLSX 30 mil linhas x 30 colunas", xlsx, "xlsx", FIM);
        Path xls = gerar("planilha-30k.xls", () -> xlsGrande(pasta.resolve("planilha-30k.xls")));
        medir("XLS equivalente", xls, "xls", FIM);
        Path docx = gerar("texto-200.docx", () -> docx(pasta.resolve("texto-200.docx"), PAGINAS_DO_DOCX));
        medir("DOCX de " + PAGINAS_DO_DOCX + " páginas", docx, "docx", "Página " + PAGINAS_DO_DOCX + ", linha 38");
        Path jpg = gerar("foto-20mp.jpg", () -> jpg(pasta.resolve("foto-20mp.jpg"), 5472, 3648));
        medir("JPG de 20 MP (5472 x 3648)", jpg, "jpg", null);

        medirDossieMisto();

        Path relatorio = Path.of("target", "desempenho-conversao.txt");
        Files.createDirectories(relatorio.getParent());
        Files.writeString(relatorio, String.join(System.lineSeparator(), linhasDoRelatorio) + System.lineSeparator());
        linhasDoRelatorio.forEach(System.out::println);
        assertThat(alertas).as("valores além dos limites (relatar, não redesenhar): %s", alertas).isEmpty();
    }

    // --- medições

    /** `marcaFinal`: texto da última linha do original, que precisa aparecer no PDF (nada cortado); nulo para imagem. */
    private void medir(String nome, Path original, String extensao, String marcaFinal) throws IOException {
        Path pdf = pasta.resolve(original.getFileName() + ".pdf");
        long entrada = Files.size(original);
        Amostrador amostrador = Amostrador.iniciar(pasta);
        long inicio = System.nanoTime();
        String situacao;
        PdfPreparado preparado = null;
        try {
            preparado = preparador.preparar(original, extensao, pdf);
            situacao = "ok";
        } catch (FalhaNoDocumentoException e) {
            situacao = "FALHOU: " + e.getMessage();
        }
        double segundos = (System.nanoTime() - inicio) / 1e9;
        Amostrador.Resultado r = amostrador.parar();
        String linha = "%s: %s; entrada %d MB; %.1f s; %s; heap %d MB (%.0f%% do máximo); soffice %d MB; disco %d MB; PDF %s"
                .formatted(nome, situacao, entrada / MB, segundos,
                        preparado == null ? "sem páginas" : preparado.paginas() + " páginas"
                                + (preparado.abasEmPaginaUnica() > 0 ? " (" + preparado.abasEmPaginaUnica() + " aba(s) em página única)" : ""),
                        r.heap() / MB, 100.0 * r.heap() / Runtime.getRuntime().maxMemory(), r.rssDoSoffice() / MB,
                        r.disco() / MB, Files.exists(pdf) ? Files.size(pdf) / MB + " MB" : "não gerado");
        registrar(linha);
        conferirLimites(nome, situacao, segundos, r);
        if (preparado != null) {
            conferirConteudo(nome, pdf, marcaFinal);
        }
        Files.deleteIfExists(pdf);
        Files.deleteIfExists(original);
    }

    /** Páginas, tamanho da primeira página e presença da última linha no texto extraído do PDF. */
    private void conferirConteudo(String nome, Path pdf, String marcaFinal) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            PDRectangle caixa = documento.getPage(0).getMediaBox();
            String presente = "n/a";
            if (marcaFinal != null) {
                String texto = new org.apache.pdfbox.text.PDFTextStripper().getText(documento);
                boolean achou = texto.contains(marcaFinal);
                presente = achou ? "sim" : "NÃO";
                if (!achou && marcaFinal.equals(FIM)) {
                    int ultima = 0;
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("Linha (\\d+)").matcher(texto);
                    while (m.find()) {
                        ultima = Math.max(ultima, Integer.parseInt(m.group(1)));
                    }
                    presente += " (a maior linha encontrada é a " + ultima + ")";
                }
                if (!achou) {
                    alertas.add(nome + ": o texto da última linha (" + marcaFinal + ") não aparece no PDF");
                }
            }
            registrar("  conferência de %s: %d páginas; 1ª página %.0f x %.0f pt; última linha no texto: %s"
                    .formatted(nome, documento.getNumberOfPages(), caixa.getWidth(), caixa.getHeight(), presente));
        }
    }

    private void medirDossieMisto() throws Exception {
        Path pdfBase = pdfDe50Mb(pasta.resolve("base.pdf"));
        List<Path> originais = new ArrayList<>();
        List<String> extensoes = new ArrayList<>();
        for (int i = 0; i < PDFS_NO_MISTO; i++) {
            originais.add(Files.copy(pdfBase, pasta.resolve("misto-pdf-" + i + ".pdf")));
            extensoes.add("pdf");
        }
        Files.delete(pdfBase);
        for (int i = 0; i < 2; i++) {
            Path p = pasta.resolve("misto-xlsx-" + i + ".xlsx");
            xlsxPequeno(p, 5000, 20);
            originais.add(p);
            extensoes.add("xlsx");
        }
        Path xls = pasta.resolve("misto.xls");
        xlsPequeno(xls, 5000, 20);
        originais.add(xls);
        extensoes.add("xls");
        for (int i = 0; i < 3; i++) {
            Path p = pasta.resolve("misto-docx-" + i + ".docx");
            docx(p, 30);
            originais.add(p);
            extensoes.add("docx");
        }
        for (int i = 0; i < 5; i++) {
            Path p = pasta.resolve("misto-foto-" + i + ".jpg");
            jpg(p, 3264, 2448);
            originais.add(p);
            extensoes.add("jpg");
        }
        for (int i = 0; i < 3; i++) {
            Path p = pasta.resolve("misto-imagem-" + i + ".png");
            png(p, 2000, 1500);
            originais.add(p);
            extensoes.add("png");
        }
        long entrada = originais.stream().mapToLong(DesempenhoDaConversaoTest::tamanho).sum();

        Amostrador amostrador = Amostrador.iniciar(pasta);
        long inicio = System.nanoTime();
        List<DocumentoParaMontagem> documentos = new ArrayList<>();
        int convertidos = 0;
        String situacao = "ok";
        for (int i = 0; i < originais.size(); i++) {
            Path pdf = pasta.resolve(String.format("%04d.pdf", i));
            try {
                PdfPreparado p = preparador.preparar(originais.get(i), extensoes.get(i), pdf);
                convertidos += p.convertidoDe() == null ? 0 : 1;
                documentos.add(new DocumentoParaMontagem("Documento " + (i + 1), "Documento do processo",
                        LocalDateTime.of(2025, 4, 1, 10, 0).plusDays(i), pdf, p.paginas(), p.convertidoDe(),
                        p.abasEmPaginaUnica()));
            } catch (FalhaNoDocumentoException e) {
                situacao = "FALHOU em " + originais.get(i).getFileName() + ": " + e.getMessage();
            }
        }
        long fimDaPreparacao = System.nanoTime();
        Path dossie = pasta.resolve("dossie-misto.pdf");
        int paginas = montador.montar(capa(), documentos, new OpcoesDeMontagem(true, true, true), dossie);
        double segundos = (System.nanoTime() - inicio) / 1e9;
        Amostrador.Resultado r = amostrador.parar();
        registrar("Dossiê misto (%d documentos: %d PDFs de ~50 MB, 3 planilhas, 3 textos, 8 imagens): %s; entrada %d MB; "
                .formatted(originais.size(), PDFS_NO_MISTO, situacao, entrada / MB)
                + "preparação (validação e conversão) %.1f s; montagem %.1f s; total %.1f s; %d documentos, %d convertidos, %d páginas; "
                        .formatted((fimDaPreparacao - inicio) / 1e9, (System.nanoTime() - fimDaPreparacao) / 1e9, segundos,
                                documentos.size(), convertidos, paginas)
                + "heap %d MB (%.0f%% do máximo); soffice %d MB; disco %d MB; PDF final %d MB"
                        .formatted(r.heap() / MB, 100.0 * r.heap() / Runtime.getRuntime().maxMemory(),
                                r.rssDoSoffice() / MB, r.disco() / MB, tamanho(dossie) / MB));
        conferirLimites("Dossiê misto", situacao, segundos, r);
    }

    private void conferirLimites(String nome, String situacao, double segundos, Amostrador.Resultado r) {
        if (!"ok".equals(situacao)) {
            alertas.add(nome + ": " + situacao);
        }
        long maximo = Runtime.getRuntime().maxMemory();
        if (r.heap() > maximo * 0.70) {
            alertas.add("%s: heap de %d MB passou de 70%% do máximo (%d MB)".formatted(nome, r.heap() / MB, maximo / MB));
        }
        if (segundos > parametros.timeoutSegundos() * 0.80) {
            alertas.add("%s: %.0f s, perto do timeout de %d s".formatted(nome, segundos, parametros.timeoutSegundos()));
        }
    }

    private void registrar(String linha) {
        linhasDoRelatorio.add(linha);
    }

    // --- amostragem: heap da JVM, memória residente do soffice e disco, a cada 50 ms (disco a cada 250 ms)

    private static final class Amostrador {

        record Resultado(long heap, long rssDoSoffice, long disco) {
        }

        private final AtomicLong heap = new AtomicLong();
        private final AtomicLong rss = new AtomicLong();
        private final AtomicLong disco = new AtomicLong();
        private Thread thread;

        static Amostrador iniciar(Path pasta) {
            System.gc();
            ManagementFactory.getMemoryPoolMXBeans().forEach(MemoryPoolMXBean::resetPeakUsage);
            Amostrador a = new Amostrador();
            a.thread = Thread.ofPlatform().daemon().start(() -> a.rodar(pasta));
            return a;
        }

        private void rodar(Path pasta) {
            Runtime rt = Runtime.getRuntime();
            int volta = 0;
            while (!Thread.currentThread().isInterrupted()) {
                heap.accumulateAndGet(rt.totalMemory() - rt.freeMemory(), Math::max);
                rss.accumulateAndGet(rssDoSoffice(), Math::max);
                if (volta++ % 5 == 0) {
                    disco.accumulateAndGet(tamanhoEmDisco(pasta), Math::max);
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        Resultado parar() {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new Resultado(heap.get(), rss.get(), disco.get());
        }

        /** Soma da memória residente (VmRSS) dos processos do LibreOffice; só no Linux (/proc), 0 fora dele. */
        private static long rssDoSoffice() {
            long total = 0;
            for (ProcessHandle p : (Iterable<ProcessHandle>) ProcessHandle.allProcesses()::iterator) {
                String comando = p.info().command().orElse("");
                if (!comando.contains("soffice") && !comando.contains("oosplash")) {
                    continue;
                }
                try {
                    for (String linha : Files.readAllLines(Path.of("/proc/" + p.pid() + "/status"))) {
                        if (linha.startsWith("VmRSS:")) {
                            total += Long.parseLong(linha.replaceAll("\\D", "")) * 1024;
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    // processo terminou entre a listagem e a leitura
                }
            }
            return total;
        }

        /** Disco usado agora pelos arquivos do teste e pelo diretório de trabalho do conversor. */
        private static long tamanhoEmDisco(Path pasta) {
            long total = soma(pasta);
            try (Stream<Path> temporarios = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
                for (Path t : temporarios.filter(p -> p.getFileName().toString().startsWith("rastro-conversor-")).toList()) {
                    total += soma(t);
                }
            } catch (IOException | RuntimeException e) {
                // sem temporários
            }
            return total;
        }

        private static long soma(Path raiz) {
            try (Stream<Path> arquivos = Files.walk(raiz)) {
                return arquivos.filter(Files::isRegularFile).mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                }).sum();
            } catch (IOException | RuntimeException e) {
                return 0;
            }
        }
    }

    // --- arquivos sintéticos

    private interface Geracao {
        void executar() throws Exception;
    }

    private Path gerar(String nome, Geracao geracao) throws Exception {
        long inicio = System.nanoTime();
        geracao.executar();
        Path arquivo = pasta.resolve(nome);
        registrar("(gerado %s: %d MB em %.1f s)".formatted(nome, tamanho(arquivo) / MB, (System.nanoTime() - inicio) / 1e9));
        return arquivo;
    }

    /** XLSX escrito à mão em fluxo (o POI em streaming grava ZIP64, que o LibreOffice não abre: R39). */
    private static void xlsxGrande(Path destino) throws IOException {
        xlsxPequeno(destino, LINHAS, COLUNAS);
    }

    private static void xlsxPequeno(Path destino, int linhas, int colunas) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(destino), 1 << 16))) {
            entrada(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "</Types>");
            entrada(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            entrada(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"Dados\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entrada(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "</Relationships>");
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            OutputStream saida = new BufferedOutputStream(zip, 1 << 16);
            saida.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
                    .getBytes(StandardCharsets.UTF_8));
            for (int l = 1; l <= linhas; l++) {
                StringBuilder linha = new StringBuilder("<row r=\"").append(l).append("\">");
                linha.append("<c r=\"A").append(l).append("\" t=\"inlineStr\"><is><t>Linha ").append(l).append("</t></is></c>");
                for (int c = 1; c < colunas; c++) {
                    linha.append("<c r=\"").append(coluna(c)).append(l).append("\"><v>").append(valor(l, c)).append("</v></c>");
                }
                saida.write(linha.append("</row>").toString().getBytes(StandardCharsets.UTF_8));
            }
            saida.write(("<row r=\"" + (linhas + 1) + "\"><c r=\"A" + (linhas + 1) + "\" t=\"inlineStr\"><is><t>" + FIM
                    + "</t></is></c></row>").getBytes(StandardCharsets.UTF_8));
            saida.write("</sheetData></worksheet>".getBytes(StandardCharsets.UTF_8));
            saida.flush();
            zip.closeEntry();
        }
    }

    private static void entrada(ZipOutputStream zip, String nome, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String coluna(int indice) {
        StringBuilder nome = new StringBuilder();
        for (int i = indice + 1; i > 0; i = (i - 1) / 26) {
            nome.insert(0, (char) ('A' + (i - 1) % 26));
        }
        return nome.toString();
    }

    private static double valor(int linha, int coluna) {
        return Math.round((linha * 31L + coluna * 17L) % 100_000) / 100.0;
    }

    private static void xlsGrande(Path destino) throws IOException {
        xlsPequeno(destino, LINHAS, COLUNAS);
    }

    private static void xlsPequeno(Path destino, int linhas, int colunas) throws IOException {
        try (HSSFWorkbook pasta = new HSSFWorkbook(); OutputStream saida = Files.newOutputStream(destino)) {
            Sheet aba = pasta.createSheet("Dados");
            for (int l = 0; l < linhas; l++) {
                Row linha = aba.createRow(l);
                linha.createCell(0).setCellValue("Linha " + (l + 1));
                for (int c = 1; c < colunas; c++) {
                    linha.createCell(c).setCellValue(valor(l + 1, c));
                }
            }
            aba.createRow(linhas).createCell(0).setCellValue(FIM);
            pasta.write(saida);
        }
    }

    private static void docx(Path destino, int paginas) throws IOException {
        try (XWPFDocument documento = new XWPFDocument(); OutputStream saida = Files.newOutputStream(destino)) {
            for (int p = 0; p < paginas; p++) {
                for (int linha = 0; linha < 38; linha++) {
                    XWPFParagraph paragrafo = documento.createParagraph();
                    paragrafo.createRun().setText("Página %d, linha %d: %s".formatted(p + 1, linha + 1, DocumentosDeTeste.TEXTO));
                }
                documento.createParagraph().createRun().addBreak(BreakType.PAGE);
            }
            documento.write(saida);
        }
    }

    /** Imagem de gradiente com ruído: não comprime a quase nada, como uma foto. */
    private static void jpg(Path destino, int largura, int altura) throws IOException {
        if (!ImageIO.write(ruido(largura, altura), "jpg", destino.toFile())) {
            throw new IOException("sem escritor de JPEG");
        }
    }

    private static void png(Path destino, int largura, int altura) throws IOException {
        if (!ImageIO.write(ruido(largura, altura), "png", destino.toFile())) {
            throw new IOException("sem escritor de PNG");
        }
    }

    private static BufferedImage ruido(int largura, int altura) {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) imagem.getRaster().getDataBuffer()).getData();
        Random aleatorio = new Random(7);
        for (int y = 0; y < altura; y++) {
            for (int x = 0; x < largura; x++) {
                int base = (x * 255 / largura) << 16 | (y * 255 / altura) << 8 | 128;
                pixels[y * largura + x] = base ^ (aleatorio.nextInt(48) * 0x010101);
            }
        }
        return imagem;
    }

    /** PDF de ~50 MB: 4 páginas de ruído sem compressão útil (como em DesempenhoDaMontagemTest). */
    private static Path pdfDe50Mb(Path destino) throws IOException {
        Random aleatorio = new Random(42);
        try (PDDocument documento = new PDDocument()) {
            for (int p = 0; p < 4; p++) {
                BufferedImage ruido = new BufferedImage(2048, 2048, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 2048; y++) {
                    for (int x = 0; x < 2048; x++) {
                        ruido.setRGB(x, y, aleatorio.nextInt(0x1000000));
                    }
                }
                PDImageXObject imagem = LosslessFactory.createFromImage(documento, ruido);
                PDPage pagina = new PDPage(PDRectangle.A4);
                documento.addPage(pagina);
                try (PDPageContentStream c = new PDPageContentStream(documento, pagina)) {
                    c.drawImage(imagem, 20, 20, PDRectangle.A4.getWidth() - 40, PDRectangle.A4.getHeight() - 40);
                }
            }
            documento.save(destino.toFile());
        }
        try (PDDocument conferido = Loader.loadPDF(destino.toFile())) {
            assertThat(conferido.getNumberOfPages()).isEqualTo(4);
        }
        return destino;
    }

    private static DadosDaCapa capa() {
        return new DadosDaCapa("Processo", "2825/2025", "Associação Residencial Vale Verde", "12.345.678/0001-90",
                "Uso de Recursos Hídricos", "Em análise", LocalDateTime.of(2025, 3, 31, 10, 12), List.of("Consumo humano"),
                List.of("Captação"), LocalDateTime.of(2026, 10, 27, 10, 0), "Gestor Demo");
    }

    private static long tamanho(Path arquivo) {
        try {
            return Files.size(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
