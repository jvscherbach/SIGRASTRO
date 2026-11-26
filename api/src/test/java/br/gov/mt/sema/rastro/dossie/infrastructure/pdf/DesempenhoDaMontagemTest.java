package br.gov.mt.sema.rastro.dossie.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie.DocumentoParaMontagem;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie.Inventario;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie.OpcoesDeMontagem;
import br.gov.mt.sema.rastro.dossie.domain.DadosDaCapa;
import br.gov.mt.sema.rastro.dossie.domain.DocumentoNaoIncluido;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Desempenho da montagem com arquivos do tamanho real da homologação (S2.2, bloco 8): 20 PDFs sintéticos de ~50 MB
 * (imagens de ruído, que não comprimem), validados e montados como na geração. Mede tempo, pico de heap e tamanho
 * final, e falha se o pico passar de 70% do heap. Desligado por padrão (gera ~2 GB em disco); para rodar com o heap
 * desejado (o do container é MaxRAMPercentage=75 do limite de memória, docs/12):
 *
 * <pre>./mvnw -Dtestes.excluidos= -Dgroups=desempenho -Dtest=DesempenhoDaMontagemTest -DargLine=-Xmx1024m test</pre>
 */
@Tag("desempenho")
class DesempenhoDaMontagemTest {

    private static final int DOCUMENTOS = 20;
    private static final int PAGINAS_POR_DOCUMENTO = 4;
    /** 2048 x 2048 RGB de ruído: ~12,6 MB por página depois do Flate, ~50 MB por documento. */
    private static final int LADO_DA_IMAGEM = 2048;

    @TempDir
    Path pasta;

    @Test
    void vinteDocumentosDe50MbMontamSemPassarDe70PorCentoDoHeap() throws Exception {
        Path base = gerarDocumentoDe50Mb(pasta.resolve("base.pdf"));
        List<DocumentoParaMontagem> documentos = new ArrayList<>();
        for (int i = 1; i <= DOCUMENTOS; i++) {
            Path copia = Files.copy(base, pasta.resolve(String.format("%04d.pdf", i)));
            documentos.add(new DocumentoParaMontagem("Documento " + i, "Documento do processo",
                    LocalDateTime.of(2025, 4, 1, 10, 0).plusDays(i), copia, PAGINAS_POR_DOCUMENTO));
        }
        Files.delete(base);
        long entrada = documentos.stream().mapToLong(d -> tamanho(d.arquivo())).sum();
        ValidadorPdfBox validador = new ValidadorPdfBox();
        Path destino = pasta.resolve("dossie.pdf");

        System.gc();
        resetarPicos();
        AtomicLong picoAmostrado = new AtomicLong();
        Thread amostrador = Thread.ofPlatform().daemon().start(() -> amostrar(picoAmostrado));
        long inicio = System.nanoTime();
        for (DocumentoParaMontagem documento : documentos) {
            assertThat(validador.validar(documento.arquivo())).isEqualTo(PAGINAS_POR_DOCUMENTO);
        }
        long fimDaValidacao = System.nanoTime();
        int paginas = new MontagemPdfBox().montar(capa(), documentos, new Inventario(DOCUMENTOS + 1, List.of(
                new DocumentoNaoIncluido(0, "Imóvel", "31", "Matrícula", null, DocumentoNaoIncluido.INDISPONIVEL))),
                new OpcoesDeMontagem(true, true, true), destino);
        long fim = System.nanoTime();
        amostrador.interrupt();
        amostrador.join();

        long maximo = Runtime.getRuntime().maxMemory();
        long picoDosPools = picoDoHeap();
        long pico = Math.max(picoAmostrado.get(), 0);
        long saida = tamanho(destino);
        String relatorio = String.format(
                "DESEMPENHO: %d documentos, %d MB de entrada; validação %.1f s; montagem %.1f s; total %.1f s; "
                        + "%d páginas; PDF final %d MB; heap máximo %d MB; pico amostrado %d MB (%.0f%%); "
                        + "soma dos picos dos pools %d MB",
                DOCUMENTOS, entrada / mb(), segundos(fimDaValidacao - inicio), segundos(fim - fimDaValidacao),
                segundos(fim - inicio), paginas, saida / mb(), maximo / mb(), pico / mb(), 100.0 * pico / maximo,
                picoDosPools / mb());
        System.out.println(relatorio);
        Files.writeString(Path.of("target", "desempenho-montagem.txt"), relatorio + System.lineSeparator());

        assertThat(paginas).isEqualTo(1 + 1 + 1 + DOCUMENTOS * PAGINAS_POR_DOCUMENTO);
        assertThat(saida).isGreaterThan(entrada * 9 / 10);
        assertThat(pico).as(relatorio).isLessThan((long) (maximo * 0.70));

        apagarTudo();
        try (Stream<Path> restantes = Files.list(pasta)) {
            assertThat(restantes).isEmpty();
        }
    }

    private static Path gerarDocumentoDe50Mb(Path destino) throws IOException {
        Random aleatorio = new Random(42);
        try (PDDocument documento = new PDDocument()) {
            for (int p = 0; p < PAGINAS_POR_DOCUMENTO; p++) {
                BufferedImage ruido = new BufferedImage(LADO_DA_IMAGEM, LADO_DA_IMAGEM, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < LADO_DA_IMAGEM; y++) {
                    for (int x = 0; x < LADO_DA_IMAGEM; x++) {
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
            assertThat(conferido.getNumberOfPages()).isEqualTo(PAGINAS_POR_DOCUMENTO);
        }
        assertThat(tamanho(destino)).as("~50 MB por documento").isBetween(40L * mb(), 60L * mb());
        return destino;
    }

    private static DadosDaCapa capa() {
        return new DadosDaCapa("Processo", "2825/2025", "Associação Residencial Vale Verde", "11.222.333/0001-81",
                "Uso de Recursos Hídricos", "Em análise", LocalDateTime.of(2025, 3, 31, 10, 12), List.of("Consumo humano"),
                List.of("Captação"), LocalDateTime.of(2026, 10, 26, 10, 0), "Gestor");
    }

    /** Uso do heap a cada 20 ms; o maior valor é o pico de verdade (a soma dos picos por pool superestima). */
    private static void amostrar(AtomicLong pico) {
        Runtime rt = Runtime.getRuntime();
        while (!Thread.currentThread().isInterrupted()) {
            pico.accumulateAndGet(rt.totalMemory() - rt.freeMemory(), Math::max);
            try {
                Thread.sleep(Duration.ofMillis(20));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void resetarPicos() {
        ManagementFactory.getMemoryPoolMXBeans().forEach(MemoryPoolMXBean::resetPeakUsage);
    }

    private static long picoDoHeap() {
        return ManagementFactory.getMemoryPoolMXBeans().stream().filter(p -> p.getType() == MemoryType.HEAP)
                .mapToLong(p -> p.getPeakUsage().getUsed()).sum();
    }

    private void apagarTudo() throws IOException {
        try (Stream<Path> arquivos = Files.list(pasta)) {
            for (Path arquivo : arquivos.toList()) {
                Files.deleteIfExists(arquivo);
            }
        }
    }

    private static long tamanho(Path arquivo) {
        try {
            return Files.size(arquivo);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static long mb() {
        return 1024L * 1024L;
    }

    private static double segundos(long nanos) {
        return nanos / 1e9;
    }
}
