package br.gov.mt.sema.vistas.integration.licenciamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.shared.application.exception.CredencialArquivosRecusadaException;
import br.gov.mt.sema.vistas.testsupport.ServidorHttpDeTeste;
import br.gov.mt.sema.vistas.testsupport.ServidorHttpDeTeste.Troca;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/** A API de arquivos é simulada por um servidor HTTP local: nenhum sistema real é tocado. */
class ArquivosDoLicenciamentoHttpTest {

    private static final String TOKEN = "TOKEN-SECRETO-DE-TESTE";
    private static final String CHAVE = "CHAVE-SECRETA-DE-TESTE";

    @TempDir
    Path pasta;

    private ServidorHttpDeTeste servidor;
    private final List<String> pedidos = new CopyOnWriteArrayList<>();
    private final AtomicReference<Troca> ultima = new AtomicReference<>();
    private ListAppender<ILoggingEvent> log;

    @BeforeEach
    void subir() throws IOException {
        servidor = ServidorHttpDeTeste.iniciar();
        log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger("br.gov.mt.sema.vistas")).addAppender(log);
    }

    @AfterEach
    void derrubar() {
        servidor.close();
        ((Logger) LoggerFactory.getLogger("br.gov.mt.sema.vistas")).detachAppender(log);
    }

    private String url() {
        return servidor.url();
    }

    private void rota(ServidorHttpDeTeste.Tratador tratador) {
        servidor.responderCom(troca -> {
            pedidos.add(troca.caminhoBruto());
            ultima.set(troca);
            tratador.tratar(troca);
        });
    }

    private ArquivosDoLicenciamentoHttp adapter(String modo, Duration limite) {
        return new ArquivosDoLicenciamentoHttp(url(), TOKEN, modo, "SESSAO", () -> limite);
    }

    private static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    // --- sucesso

    @Test
    void grava200DiretoNoArquivo() throws IOException {
        rota(troca -> troca.responder(200, bytes("%PDF-1.4 conteudo")));
        Path destino = pasta.resolve("a.pdf");

        adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, destino);

        assertThat(Files.readString(destino)).isEqualTo("%PDF-1.4 conteudo");
        assertThat(pedidos).containsExactly("/api/arquivo/" + CHAVE);
    }

    @Test
    void arquivoGrandeEmStreamingChegaInteiro() throws IOException {
        final int blocos = 64;
        final byte[] bloco = new byte[1024 * 1024];
        rota(troca -> {
            try (OutputStream saida = troca.iniciarEmPartes(200)) {
                for (int i = 0; i < blocos; i++) {
                    saida.write(bloco);
                }
            }
        });
        Path destino = pasta.resolve("grande.pdf");

        adapter("bearer", Duration.ofSeconds(30)).baixar(CHAVE, destino);

        assertThat(Files.size(destino)).isEqualTo((long) blocos * bloco.length);
    }

    // --- autenticação

    @Test
    void modoBearerEnviaAuthorizationEMaisNada() throws IOException {
        rota(troca -> troca.responder(200, bytes("x")));

        adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf"));

        assertThat(ultima.get().cabecalho("Authorization")).isEqualTo("Bearer " + TOKEN);
        assertThat(ultima.get().cabecalho("Cookie")).isNull();
    }

    @Test
    void modoPadraoVazioTambemEBearer() throws IOException {
        rota(troca -> troca.responder(200, bytes("x")));

        adapter("", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf"));

        assertThat(ultima.get().cabecalho("Authorization")).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void modoCookieEnviaNomeIgualAoTokenEMaisNada() throws IOException {
        rota(troca -> troca.responder(200, bytes("x")));

        adapter("cookie", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf"));

        assertThat(ultima.get().cabecalho("Cookie")).isEqualTo("SESSAO=" + TOKEN);
        assertThat(ultima.get().cabecalho("Authorization")).isNull();
    }

    @Test
    void tokenVazioNaoEnviaCabecalhoEORepositorioRecusa() {
        rota(troca -> troca.responder(401, new byte[0]));
        ArquivosDoLicenciamentoHttp semToken = new ArquivosDoLicenciamentoHttp(url(), "", "bearer", "", () -> Duration.ofSeconds(5));

        assertThatThrownBy(() -> semToken.baixar(CHAVE, pasta.resolve("a.pdf")))
                .isInstanceOf(CredencialArquivosRecusadaException.class);
        assertThat(ultima.get().cabecalho("Authorization")).isNull();
    }

    // --- tradução de erros

    @Test
    void status401E403InterrompemTudoComMensagemSemOToken() {
        for (int status : new int[] {401, 403}) {
            rota(troca -> troca.responder(status, new byte[0]));

            assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf")))
                    .isInstanceOf(CredencialArquivosRecusadaException.class)
                    .hasMessage("Token da API de arquivos expirado ou recusado: capture um novo token.")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(TOKEN));
        }
    }

    @Test
    void status404EFalhaDoDocumento() {
        rota(troca -> troca.responder(404, new byte[0]));

        assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf")))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("arquivo não encontrado no repositório");
    }

    @Test
    void status503E500SaoRepositorioIndisponivel() {
        for (int status : new int[] {503, 500}) {
            rota(troca -> troca.responder(status, new byte[0]));

            assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf")))
                    .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("repositório de arquivos indisponível");
        }
    }

    @Test
    void outroStatusEFalhaDoDocumentoSemVazarNada() {
        rota(troca -> troca.responder(418, new byte[0]));

        assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf")))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessageContaining("418")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CHAVE, TOKEN, url()));
    }

    @Test
    void timeoutAntesDoCabecalhoEFalhaPorTempoLimiteESemArquivo() {
        rota(troca -> {
            dormir(3000);
            troca.responder(200, bytes("x"));
        });
        Path destino = pasta.resolve("a.pdf");

        assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(1)).baixar(CHAVE, destino))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("tempo limite excedido ao obter o arquivo");
        assertThat(destino).doesNotExist();
    }

    @Test
    void corpoQueParaDeChegarNoMeioEstouraOTempoLimiteEOParcialEApagado() {
        rota(troca -> {
            OutputStream saida = troca.iniciarEmPartes(200);
            saida.write(new byte[4096]);
            saida.flush();
            dormir(3000);
        });
        Path destino = pasta.resolve("parcial.pdf");

        assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(1)).baixar(CHAVE, destino))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("tempo limite excedido ao obter o arquivo");
        assertThat(destino).doesNotExist();
    }

    private static void dormir(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void conexaoRecusadaEIndisponibilidadeSemMostrarAUrl() throws IOException {
        int portaFechada;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            portaFechada = socket.getLocalPort();
        }
        ArquivosDoLicenciamentoHttp fora = new ArquivosDoLicenciamentoHttp("http://127.0.0.1:" + portaFechada, TOKEN,
                "bearer", "", () -> Duration.ofSeconds(2));

        assertThatThrownBy(() -> fora.baixar(CHAVE, pasta.resolve("a.pdf")))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("repositório de arquivos indisponível")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("127.0.0.1"));
    }

    // --- chave e URL

    @Test
    void chaveZeroNuncaEhRequisitada() {
        rota(troca -> troca.responder(200, bytes("x")));

        for (String chave : new String[] {"0", " 0 ", "", null}) {
            assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(5)).baixar(chave, pasta.resolve("a.pdf")))
                    .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("arquivo indisponível no repositório");
        }
        assertThat(pedidos).isEmpty();
    }

    @Test
    void chaveComCaracteresEspeciaisVaiCodificadaNoCaminho() throws IOException {
        rota(troca -> troca.responder(200, bytes("x")));

        adapter("bearer", Duration.ofSeconds(5)).baixar("a b/c", pasta.resolve("a.pdf"));

        assertThat(pedidos).containsExactly("/api/arquivo/a%20b%2Fc");
    }

    @Test
    void barraFinalNaUrlBaseNaoDuplicaOCaminho() throws IOException {
        rota(troca -> troca.responder(200, bytes("x")));
        ArquivosDoLicenciamentoHttp comBarra = new ArquivosDoLicenciamentoHttp(url() + "//", TOKEN, "bearer", "",
                () -> Duration.ofSeconds(5));

        comBarra.baixar("K1", pasta.resolve("a.pdf"));

        assertThat(pedidos).containsExactly("/api/arquivo/K1");
    }

    // --- logs

    @Test
    void nenhumLogTrazTokenChaveOuUrl() {
        rota(troca -> troca.responder(418, new byte[0]));
        try {
            adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf"));
        } catch (FalhaNoDocumentoException esperado) {
            // o interesse é o que foi para o log
        }

        assertThat(log.list).isNotEmpty();
        assertThat(log.list).allSatisfy(evento -> assertThat(evento.getFormattedMessage())
                .doesNotContain(TOKEN, CHAVE, url()));
    }

    // --- API não configurada

    @Test
    void semUrlADocumentoFalhaComMotivoExplicito() {
        ArquivosNaoConfigurados nao = new ArquivosNaoConfigurados();

        assertThatThrownBy(() -> nao.baixar(CHAVE, pasta.resolve("a.pdf")))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("API de arquivos não configurada");
    }
}
