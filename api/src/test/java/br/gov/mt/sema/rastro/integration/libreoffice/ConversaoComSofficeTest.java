package br.gov.mt.sema.rastro.integration.libreoffice;

import static br.gov.mt.sema.rastro.integration.libreoffice.ConversorLibreOfficeTest.OFFICE_HOME;
import static br.gov.mt.sema.rastro.integration.libreoffice.ConversorLibreOfficeTest.pagina;
import static br.gov.mt.sema.rastro.integration.libreoffice.ConversorLibreOfficeTest.texto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import br.gov.mt.sema.rastro.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.rastro.documento.domain.TipoDetectado;
import br.gov.mt.sema.rastro.documento.domain.TipoReal;
import br.gov.mt.sema.rastro.integration.libreoffice.ConversorLibreOfficeTest.Parametros;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Conversão real pelo LibreOffice: textos (DOCX, DOC, ODT, RTF) e HTML, senha, arquivo corrompido e HTML com recursos
 * remotos. Exige o LibreOffice; sem ele, a classe é pulada com aviso. Um só pool para a classe (subir o soffice custa
 * alguns segundos); ao final, nenhum processo pode sobrar.
 */
class ConversaoComSofficeTest {

    static Path base;
    static ConversorLibreOffice conversor;

    @TempDir
    Path pasta;

    @BeforeAll
    static void subir() throws IOException {
        if (OFFICE_HOME != null) {
            base = Files.createTempDirectory("conversor-teste");
            conversor = ConversorLibreOfficeTest.conversor(Parametros.padrao(), OFFICE_HOME, base);
        }
    }

    /** Por teste, e não no BeforeAll, para que cada um apareça como pulado no relatório, com a mensagem. */
    @BeforeEach
    void exigirSoffice() {
        ConversorLibreOfficeTest.exigirSoffice();
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

    @Test
    void converteDocxMantendoTextoEAcentos() throws Exception {
        Path pdf = converter("docx", DocumentosDeTeste.docx(DocumentosDeTeste.TEXTO));
        assertThat(texto(pdf)).contains(DocumentosDeTeste.TEXTO);
    }

    @Test
    void converteOdtRtfEHtml() throws Exception {
        assertThat(texto(converter("odt", DocumentosDeTeste.odt(DocumentosDeTeste.TEXTO))))
                .contains(DocumentosDeTeste.TEXTO);
        assertThat(texto(converter("rtf", DocumentosDeTeste.rtf(DocumentosDeTeste.TEXTO))))
                .contains(DocumentosDeTeste.TEXTO);
        byte[] html = DocumentosDeTeste.html("<h1>Relatório</h1><table border=\"1\"><tr><td>"
                + DocumentosDeTeste.TEXTO + "</td></tr></table>");
        assertThat(texto(converter("html", html))).contains("Relatório").contains(DocumentosDeTeste.TEXTO);
    }

    @Test
    void converteDocAntigo() throws Exception {
        Path doc = DocumentosDeTeste.doc(Files.createDirectories(pasta.resolve("gerar-doc")), DocumentosDeTeste.TEXTO,
                OFFICE_HOME.toPath());
        assertThat(texto(converter("doc", Files.readAllBytes(doc)))).contains(DocumentosDeTeste.TEXTO);
    }

    @Test
    void mantemTamanhoEOrientacaoDoDocumentoSemForcarA4() throws Exception {
        // A4 paisagem e Carta retrato: o PDF segue o documento (RNF06).
        PDRectangle paisagem = pagina(converter("docx", DocumentosDeTeste.docx("paisagem", 16838, 11906)));
        PDRectangle carta = pagina(converter("docx", DocumentosDeTeste.docx("carta", 12240, 15840)));

        assertThat(paisagem.getWidth()).isCloseTo(PDRectangle.A4.getHeight(), offset(2f));
        assertThat(paisagem.getHeight()).isCloseTo(PDRectangle.A4.getWidth(), offset(2f));
        assertThat(carta.getWidth()).isCloseTo(PDRectangle.LETTER.getWidth(), offset(2f));
        assertThat(carta.getHeight()).isCloseTo(PDRectangle.LETTER.getHeight(), offset(2f));
    }

    @Test
    void htmlSemTamanhoDePaginaSaiEmA4() throws Exception {
        PDRectangle html = pagina(converter("html", DocumentosDeTeste.html("<p>sem tamanho de página</p>")));
        assertThat(html.getWidth()).isCloseTo(PDRectangle.A4.getWidth(), offset(2f));
    }

    @Test
    void documentoComSenhaFalhaComMotivoProprio() throws Exception {
        ConversorLibreOfficeTest.assertFalha(pasta, conversor, "docx",
                DocumentosDeTeste.docxCriptografado(DocumentosDeTeste.TEXTO), Motivo.PROTEGIDO_POR_SENHA);
    }

    @Test
    void arquivoCorrompidoFalhaSemDeixarNadaParaTras() throws Exception {
        ConversorLibreOfficeTest.assertFalha(pasta, conversor, "docx", DocumentosDeTeste.zipCorrompido(),
                Motivo.CORROMPIDO);
    }

    @Test
    void htmlComRecursosRemotosNaoFazNenhumaRequisicao() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            AtomicInteger conexoes = new AtomicInteger();
            Thread aceitador = new Thread(() -> {
                while (!servidor.isClosed()) {
                    try (Socket socket = servidor.accept()) {
                        conexoes.incrementAndGet();
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            aceitador.setDaemon(true);
            aceitador.start();
            String url = "http://127.0.0.1:" + servidor.getLocalPort() + "/";
            byte[] html = """
                    <!DOCTYPE html><html><head><meta charset="utf-8">
                    <link rel="stylesheet" href="%1$sestilo.css">
                    <style>@import url("%1$simport.css"); body { background: url(%1$sfundo.png) }</style>
                    <script src="%1$sscript.js"></script></head>
                    <body background="%1$sfundo2.png"><p>%2$s</p>
                    <img src="%1$simagem.png"><iframe src="%1$squadro.html"></iframe>
                    <object data="%1$sobjeto"></object><embed src="%1$sembutido">
                    <div style="background-image:url(%1$sdiv.png)">div</div></body></html>
                    """.formatted(url, DocumentosDeTeste.TEXTO).getBytes(StandardCharsets.UTF_8);

            Path pdf = converter("html", html);

            assertThat(texto(pdf)).contains(DocumentosDeTeste.TEXTO);
            assertThat(conexoes.get()).as("conexões ao servidor local").isZero();
        }
    }

    @Test
    void origemIntactaETrabalhoApagado() throws Exception {
        byte[] bytes = DocumentosDeTeste.docx(DocumentosDeTeste.TEXTO);
        Path pedido = Files.createDirectories(pasta.resolve("pedido"));
        Path origem = Files.write(pedido.resolve("original.bin"), bytes);
        Path destino = pedido.resolve("convertido.pdf");

        conversor.converter(origem, new TipoDetectado(TipoReal.TEXTO, "docx"), destino);

        assertThat(Files.readAllBytes(origem)).isEqualTo(bytes);
        try (Stream<Path> itens = Files.list(pedido)) {
            assertThat(itens.map(p -> p.getFileName().toString()))
                    .containsExactlyInAnyOrder("original.bin", "convertido.pdf");
        }
    }

    private Path converter(String extensao, byte[] conteudo) throws IOException {
        return ConversorLibreOfficeTest.converter(pasta, conversor, extensao, conteudo, TipoReal.TEXTO);
    }
}
