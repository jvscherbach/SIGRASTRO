package br.gov.mt.sema.vistas.integration.libreoffice;

import static br.gov.mt.sema.vistas.integration.libreoffice.ConversorLibreOfficeTest.OFFICE_HOME;
import static br.gov.mt.sema.vistas.integration.libreoffice.ConversorLibreOfficeTest.texto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.vistas.documento.domain.DetectorDeTipoReal;
import br.gov.mt.sema.vistas.documento.domain.TipoDetectado;
import br.gov.mt.sema.vistas.documento.domain.TipoReal;
import br.gov.mt.sema.vistas.integration.libreoffice.ConversorLibreOfficeTest.Parametros;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Arquivos hostis (S3, bloco 8). Para cada um: falha clara do documento ou conversão sem efeito colateral, dentro do
 * tempo limite. Nenhuma conexão chega à armadilha local (todo endereço remoto aponta para ela), nenhuma macro grava o
 * marcador e, ao final, nenhum {@code soffice} sobra. Exige o LibreOffice (no CI, falha sem ele).
 */
class ArquivosHostisTest {

    /** Tempo limite do pool desta classe; as planilhas enormes podem esbarrar nele, e isso é uma falha clara. */
    private static final int TIMEOUT_S = 30;
    private static final Duration TETO_POR_ARQUIVO = Duration.ofSeconds(TIMEOUT_S * 2L + 30);

    static Path base;
    static ConversorLibreOffice conversor;
    static Armadilha armadilha;

    @TempDir
    Path pasta;

    private long inicio;

    @BeforeAll
    static void subir() throws IOException {
        armadilha = new Armadilha();
        if (OFFICE_HOME != null) {
            base = Files.createTempDirectory("conversor-hostil");
            conversor = ConversorLibreOfficeTest.conversor(new Parametros(true, TIMEOUT_S, 1, 50), OFFICE_HOME, base);
        }
    }

    @BeforeEach
    void exigirSoffice() {
        inicio = System.nanoTime();
        ConversorLibreOfficeTest.exigirSoffice();
    }

    @AfterEach
    void semRequisicaoEDentroDoTempo() {
        if (conversor == null) {
            return; // pulado sem LibreOffice
        }
        assertThat(armadilha.conexoes.get()).as("conexões à armadilha local").isZero();
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).as("tempo do teste").isLessThan(TETO_POR_ARQUIVO);
    }

    @AfterAll
    static void descer() throws IOException {
        armadilha.close();
        if (conversor != null) {
            String marca = conversor.marca();
            conversor.destroy();
            assertThat(ProcessosDoConversor.comMarca(marca)).as("nenhum soffice órfão").isEmpty();
            ConversorLibreOffice.apagarPasta(base);
        }
    }

    @Test
    void armadilhaContaConexoes() throws Exception {
        // Controle: a armadilha realmente percebe uma conexão (senão "zero conexões" não provaria nada).
        try (Armadilha controle = new Armadilha(); Socket socket = new Socket(InetAddress.getLoopbackAddress(), controle.porta())) {
            long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (controle.conexoes.get() == 0 && System.nanoTime() < limite) {
                Thread.sleep(20);
            }
            assertThat(controle.conexoes.get()).isEqualTo(1);
        }
    }

    @Test
    void macroBasicAoAbrirNuncaExecuta() throws Exception {
        Path marcador = pasta.resolve("macro-executada.txt");
        Path pdf = converter("odt", ArquivosHostis.odtComMacroAoAbrir(marcador), TipoReal.TEXTO);

        assertThat(texto(pdf)).contains(ArquivosHostis.VISIVEL);
        assertThat(marcador).as("marcador gravado pela macro").doesNotExist();
    }

    @Test
    void docxEXlsxComVbaFicticioConvertemSemExecutarNada() throws Exception {
        byte[] docx = ArquivosHostis.comVbaFicticio(DocumentosDeTeste.docx(ArquivosHostis.VISIVEL), "word");
        assertThat(texto(converter("docx", docx, TipoReal.TEXTO))).contains(ArquivosHostis.VISIVEL);

        byte[] xlsx = ArquivosHostis.comVbaFicticio(PlanilhasDeTeste.xlsx(pasta -> PlanilhasDeTeste.texto(
                pasta.createSheet("Plan1"), 0, 0, ArquivosHostis.VISIVEL)), "xl");
        assertThat(texto(converter("xlsx", xlsx, TipoReal.PLANILHA))).contains(ArquivosHostis.VISIVEL);
    }

    @Test
    void planilhaOdsComVinculosEFormulasExternasUsaOsValoresGuardados() throws Exception {
        String pdf = texto(converter("ods", ArquivosHostis.odsComVinculosExternos(armadilha.url()), TipoReal.PLANILHA));

        assertThat(pdf).contains(ArquivosHostis.VISIVEL);
        assertThat(pdf).as("conteúdo de arquivo local").doesNotContain("root:");
    }

    @Test
    void planilhaXlsxComVinculoExternoNaoBuscaNada() throws Exception {
        String pdf = texto(converter("xlsx", ArquivosHostis.xlsxComVinculoExterno(armadilha.url()), TipoReal.PLANILHA));
        assertThat(pdf).contains(ArquivosHostis.VISIVEL);
    }

    @Test
    void docxComImagemECamposRemotosNaoBuscaNada() throws Exception {
        String pdf = texto(converter("docx", ArquivosHostis.docxComImagemRemota(armadilha.url()), TipoReal.TEXTO));
        assertThat(pdf).contains(ArquivosHostis.VISIVEL);
    }

    @Test
    void htmlComOutrosVetoresDeRequisicaoNaoBuscaNada() throws Exception {
        String url = armadilha.url();
        byte[] html = """
                <!DOCTYPE html><html><head><meta charset="utf-8">
                <meta http-equiv="refresh" content="0;url=%1$srefresh"><base href="%1$s">
                <link rel="preload" href="%1$spreload.js" as="script"><link rel="icon" href="%1$sicone.png">
                <style>@font-face { font-family: x; src: url(%1$sfonte.woff) }</style></head>
                <body><p>%2$s</p><img src="relativa.png"><img srcset="%1$s1x.png 1x, %1$s2x.png 2x">
                <picture><source srcset="%1$sfonte.webp"><img src="data:,"></picture>
                <svg><image href="%1$ssvg.png" width="10" height="10"/></svg>
                <video poster="%1$sposter.png" src="%1$svideo.mp4"></video><audio src="%1$saudio.mp3"></audio>
                <input type="image" src="%1$sbotao.png"><table background="%1$stabela.png"><tr><td>t</td></tr></table>
                <a href="%1$slink">link não é seguido</a></body></html>
                """.formatted(url, ArquivosHostis.VISIVEL).getBytes(StandardCharsets.UTF_8);

        assertThat(texto(converter("html", html, TipoReal.TEXTO))).contains(ArquivosHostis.VISIVEL);
    }

    @Test
    void zipBombaRotuladoComoDocxFalhaSemChegarAoLibreOffice() throws Exception {
        Path pedido = Files.createDirectories(pasta.resolve("bomba"));
        Path bomba = ArquivosHostis.zipBombaComoDocx(pedido.resolve("origem.pdf"),
                ZipLimitado.DESCOMPACTADO_MAXIMO + 64L * 1024 * 1024);
        assertThat(Files.size(bomba)).as("comprimido").isLessThan(20L * 1024 * 1024);
        TipoDetectado tipo = DetectorDeTipoReal.detectar(bomba, "pdf");
        assertThat(tipo).isEqualTo(new TipoDetectado(TipoReal.TEXTO, "docx"));

        assertThatThrownBy(() -> conversor.converter(bomba, tipo, pedido.resolve("saida.pdf")))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        f -> assertThat(f.motivo()).isEqualTo(Motivo.ENTRADA_GRANDE_DEMAIS));
        assertThat(conteudoDe(pedido)).containsExactly("origem.pdf");
    }

    @Test
    void extensaoTrocadaValeOConteudo() throws Exception {
        Path pedido = Files.createDirectories(pasta.resolve("trocada"));
        Path planilhaComoPdf = Files.write(pedido.resolve("relatorio.pdf"), PlanilhasDeTeste.xlsx(
                p -> PlanilhasDeTeste.texto(p.createSheet("Plan1"), 0, 0, ArquivosHostis.VISIVEL)));
        TipoDetectado tipo = DetectorDeTipoReal.detectar(planilhaComoPdf, "pdf");
        assertThat(tipo).isEqualTo(new TipoDetectado(TipoReal.PLANILHA, "xlsx"));
        conversor.converter(planilhaComoPdf, tipo, pedido.resolve("saida.pdf"));
        assertThat(texto(pedido.resolve("saida.pdf"))).contains(ArquivosHostis.VISIVEL);

        // Bytes sem assinatura conhecida com nome de DOCX não chegam ao conversor de documentos.
        Path lixoComoDocx = Files.write(pedido.resolve("contrato.docx"), "nada de docx aqui".getBytes(StandardCharsets.UTF_8));
        TipoDetectado desconhecido = DetectorDeTipoReal.detectar(lixoComoDocx, "docx");
        assertThat(desconhecido.tipo()).isEqualTo(TipoReal.DESCONHECIDO);
        assertThatThrownBy(() -> conversor.converter(lixoComoDocx, desconhecido, pedido.resolve("outra.pdf")))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        f -> assertThat(f.motivo()).isEqualTo(Motivo.FORMATO_NAO_SUPORTADO));
    }

    @Test
    void arquivoDeZeroBytesFalhaComoCorrompidoSemGerarPaginaEmBranco() throws Exception {
        for (String extensao : new String[] {"docx", "xlsx", "odt", "rtf", "html"}) {
            TipoReal tipo = extensao.equals("xlsx") ? TipoReal.PLANILHA : TipoReal.TEXTO;
            Path pedido = Files.createDirectories(pasta.resolve("vazio-" + extensao));
            Path origem = Files.write(pedido.resolve("origem." + extensao), new byte[0]);
            assertThatThrownBy(() -> conversor.converter(origem, new TipoDetectado(tipo, extensao), pedido.resolve("saida.pdf")))
                    .as(extensao)
                    .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                            f -> assertThat(f.motivo()).isEqualTo(Motivo.CORROMPIDO));
            assertThat(conteudoDe(pedido)).containsExactly("origem." + extensao);
        }
    }

    @Test
    void planilhaComMilharesDeAbasFalhaPeloLimiteOuPeloTempo() throws Exception {
        long antes = System.nanoTime();
        assertFalhaEntre("xlsx", ArquivosHostis.xlsxComAbas(3000), TipoReal.PLANILHA,
                Set.of(Motivo.PAGINAS_DEMAIS, Motivo.TEMPO_LIMITE));
        System.out.printf(Locale.ROOT, "[medição] 3000 abas: %d s%n", Duration.ofNanos(System.nanoTime() - antes).toSeconds());
    }

    @Test
    void planilhaComUmMilhaoDeLinhasFalhaPeloLimiteOuPeloTempo() throws Exception {
        Path pedido = Files.createDirectories(pasta.resolve("linhas"));
        Path origem = ArquivosHostis.xlsxComLinhas(pedido.resolve("origem.xlsx"), 1_000_000, false);
        long antes = System.nanoTime();
        assertThatThrownBy(() -> conversor.converter(origem, new TipoDetectado(TipoReal.PLANILHA, "xlsx"),
                pedido.resolve("saida.pdf")))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        f -> assertThat(f.motivo()).isIn(Motivo.PAGINAS_DEMAIS, Motivo.TEMPO_LIMITE));
        System.out.printf(Locale.ROOT, "[medição] 1 milhão de linhas (%d MB): %d s%n", Files.size(origem) / (1024 * 1024),
                Duration.ofNanos(System.nanoTime() - antes).toSeconds());
        assertThat(conteudoDe(pedido)).containsExactly("origem.xlsx");
        // Depois do tempo limite o pool sobe outro processo: a próxima conversão funciona.
        assertThat(texto(converter("rtf", DocumentosDeTeste.rtf("depois"), TipoReal.TEXTO))).contains("depois");
    }

    @Test
    void xlsxEmZip64DoPoiStreamingFalhaComoCorrompido() throws Exception {
        // Comportamento atual registrado (docs/10, R39): o LibreOffice não abre XLSX em ZIP64, formato padrão do POI no
        // modo streaming. Falha clara, rápida e sem efeito colateral; se um dia for tratado, este teste muda.
        Path pedido = Files.createDirectories(pasta.resolve("zip64"));
        Path origem = ArquivosHostis.xlsxComLinhas(pedido.resolve("origem.xlsx"), 10, true);
        assertThatThrownBy(() -> conversor.converter(origem, new TipoDetectado(TipoReal.PLANILHA, "xlsx"),
                pedido.resolve("saida.pdf")))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        f -> assertThat(f.motivo()).isEqualTo(Motivo.CORROMPIDO));
        assertThat(conteudoDe(pedido)).containsExactly("origem.xlsx");
    }

    @Test
    void nomeDeArquivoComPontosEControleNaoAfetaACopiaDeTrabalho() throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String nome = windows ? "..;evil ..docx" : "..\u0001evil\n;$(touch x) ..docx";
        Path pedido = Files.createDirectories(pasta.resolve("nome"));
        Path origem = Files.write(pedido.resolve(nome), DocumentosDeTeste.docx(ArquivosHostis.VISIVEL));

        conversor.converter(origem, new TipoDetectado(TipoReal.TEXTO, "docx"), pedido.resolve("saida.pdf"));

        assertThat(texto(pedido.resolve("saida.pdf"))).contains(ArquivosHostis.VISIVEL);
        assertThat(conteudoDe(pedido)).containsExactlyInAnyOrder(nome, "saida.pdf");
        assertThat(pasta.getParent().resolve("x")).doesNotExist();
    }

    // ---- apoio ----

    private Path converter(String extensao, byte[] conteudo, TipoReal tipo) throws IOException {
        return ConversorLibreOfficeTest.converter(pasta, conversor, extensao, conteudo, tipo);
    }

    private void assertFalhaEntre(String extensao, byte[] conteudo, TipoReal tipo, Set<Motivo> aceitos) throws IOException {
        Path pedido = Files.createTempDirectory(pasta, "pedido");
        Path origem = Files.write(pedido.resolve("origem." + extensao), conteudo);
        assertThatThrownBy(() -> conversor.converter(origem, new TipoDetectado(tipo, extensao), pedido.resolve("saida.pdf")))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class, f -> assertThat(aceitos).contains(f.motivo()));
        assertThat(conteudoDe(pedido)).containsExactly("origem." + extensao);
    }

    private static java.util.List<String> conteudoDe(Path pedido) throws IOException {
        try (Stream<Path> itens = Files.list(pedido)) {
            return itens.map(p -> p.getFileName().toString()).toList();
        }
    }

    /** Servidor local que só conta conexões: nenhuma conversão pode chegar a ele. */
    static final class Armadilha implements AutoCloseable {

        final AtomicInteger conexoes = new AtomicInteger();
        private final ServerSocket servidor;

        Armadilha() throws IOException {
            servidor = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread aceitador = new Thread(() -> {
                while (!servidor.isClosed()) {
                    try (Socket socket = servidor.accept()) {
                        conexoes.incrementAndGet();
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "armadilha");
            aceitador.setDaemon(true);
            aceitador.start();
        }

        int porta() {
            return servidor.getLocalPort();
        }

        String url() {
            return "http://127.0.0.1:" + porta() + "/";
        }

        @Override
        public void close() throws IOException {
            servidor.close();
        }
    }
}
