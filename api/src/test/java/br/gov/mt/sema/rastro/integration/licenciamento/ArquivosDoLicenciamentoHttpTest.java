package br.gov.mt.sema.rastro.integration.licenciamento;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.rastro.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.rastro.shared.application.exception.CredencialArquivosRecusadaException;
import br.gov.mt.sema.rastro.testsupport.ServidorHttpDeTeste;
import br.gov.mt.sema.rastro.testsupport.ServidorHttpDeTeste.Troca;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
        ((Logger) LoggerFactory.getLogger("br.gov.mt.sema.rastro")).addAppender(log);
    }

    @AfterEach
    void derrubar() {
        servidor.close();
        ((Logger) LoggerFactory.getLogger("br.gov.mt.sema.rastro")).detachAppender(log);
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

    /** Esperas entre tentativas pedidas pelo adapter (o teste não espera de verdade). */
    private final List<Duration> esperas = new CopyOnWriteArrayList<>();

    private static Supplier<LimitesDeDownload> limites(long segundos) {
        return limites(Duration.ofSeconds(segundos), Duration.ofMinutes(5));
    }

    private static Supplier<LimitesDeDownload> limites(Duration inatividade, Duration total) {
        return () -> new LimitesDeDownload(Duration.ofSeconds(5), inatividade, total);
    }

    private ArquivosDoLicenciamentoHttp adapter(String modo, Duration inatividade) {
        return adapter(modo, inatividade, Duration.ofMinutes(5));
    }

    private ArquivosDoLicenciamentoHttp adapter(String modo, Duration inatividade, Duration total) {
        return new ArquivosDoLicenciamentoHttp(url(), TOKEN, modo, "SESSAO", limites(inatividade, total), esperas::add);
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
        ArquivosDoLicenciamentoHttp semToken = new ArquivosDoLicenciamentoHttp(url(), "", "bearer", "", limites(5), esperas::add);

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
                "bearer", "", limites(2), esperas::add);

        assertThatThrownBy(() -> fora.baixar(CHAVE, pasta.resolve("a.pdf")))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("repositório de arquivos indisponível")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("127.0.0.1"));
    }

    // --- novas tentativas e teto total (S2.2)

    @Test
    void respostaLentaQuePassaDoTempoDeInatividadeTentaTresVezesEApagaOTemporario() {
        rota(troca -> {
            OutputStream saida = troca.iniciarEmPartes(200);
            saida.write(new byte[4096]);
            saida.flush();
            dormir(1500);
        });
        Path destino = pasta.resolve("lento.pdf");

        assertThatThrownBy(() -> adapter("bearer", Duration.ofMillis(300)).baixar(CHAVE, destino))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("tempo limite excedido ao obter o arquivo")
                .satisfies(e -> assertThat(((FalhaNoDocumentoException) e).tentativas()).isEqualTo(3));
        assertThat(pedidos).hasSize(3);
        assertThat(esperas).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(5));
        assertThat(destino).doesNotExist();
    }

    @Test
    void status503SeguidoDe200FazUmaNovaTentativaEInformaDuasTentativas() throws IOException {
        AtomicInteger chamadas = new AtomicInteger();
        rota(troca -> {
            if (chamadas.incrementAndGet() == 1) {
                troca.responder(503, new byte[0]);
            } else {
                troca.responder(200, bytes("%PDF-1.4 depois"));
            }
        });
        Path destino = pasta.resolve("a.pdf");

        int tentativas = adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, destino);

        assertThat(tentativas).isEqualTo(2);
        assertThat(pedidos).hasSize(2);
        assertThat(esperas).containsExactly(Duration.ofSeconds(2));
        assertThat(Files.readString(destino)).isEqualTo("%PDF-1.4 depois");
    }

    @Test
    void semFalhaUmaTentativaSoESemEspera() throws IOException {
        rota(troca -> troca.responder(200, bytes("x")));

        assertThat(adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf"))).isEqualTo(1);
        assertThat(esperas).isEmpty();
    }

    @Test
    void status404NaoGanhaNovaTentativa() {
        rota(troca -> troca.responder(404, new byte[0]));
        Path destino = pasta.resolve("a.pdf");

        assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, destino))
                .isInstanceOf(FalhaNoDocumentoException.class)
                .satisfies(e -> assertThat(((FalhaNoDocumentoException) e).tentativas()).isEqualTo(1));
        assertThat(pedidos).hasSize(1);
        assertThat(esperas).isEmpty();
        assertThat(destino).doesNotExist();
    }

    @Test
    void status401E403NaoGanhamNovaTentativa() {
        for (int status : new int[] {401, 403}) {
            pedidos.clear();
            rota(troca -> troca.responder(status, new byte[0]));

            assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf")))
                    .isInstanceOf(CredencialArquivosRecusadaException.class);
            assertThat(pedidos).hasSize(1);
        }
        assertThat(esperas).isEmpty();
    }

    @Test
    void status500NaoGanhaNovaTentativaSo503() {
        rota(troca -> troca.responder(500, new byte[0]));

        assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(5)).baixar(CHAVE, pasta.resolve("a.pdf")))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("repositório de arquivos indisponível");
        assertThat(pedidos).hasSize(1);
    }

    @Test
    void quedaDeConexaoGanhaNovasTentativas() throws IOException {
        int portaFechada;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            portaFechada = socket.getLocalPort();
        }
        ArquivosDoLicenciamentoHttp fora = new ArquivosDoLicenciamentoHttp("http://127.0.0.1:" + portaFechada, TOKEN,
                "bearer", "", limites(2), esperas::add);

        assertThatThrownBy(() -> fora.baixar(CHAVE, pasta.resolve("a.pdf")))
                .isInstanceOf(FalhaNoDocumentoException.class)
                .satisfies(e -> assertThat(((FalhaNoDocumentoException) e).tentativas()).isEqualTo(3));
        assertThat(esperas).hasSize(2);
    }

    @Test
    void tetoTotalInterrompeCorpoQueNuncaParaDeChegarSemNovaTentativa() {
        rota(troca -> {
            try (OutputStream saida = troca.iniciarEmPartes(200)) {
                for (int i = 0; i < 40; i++) {
                    saida.write(new byte[1024]);
                    saida.flush();
                    dormir(100);
                }
            }
        });
        Path destino = pasta.resolve("eterno.pdf");

        assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(2), Duration.ofMillis(600)).baixar(CHAVE, destino))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("tempo total máximo excedido ao obter o arquivo");
        assertThat(pedidos).hasSize(1);
        assertThat(esperas).isEmpty();
        assertThat(destino).doesNotExist();
    }

    @Test
    void novaTentativaQueNaoCabeNoTetoTotalNaoEFeita() {
        rota(troca -> troca.responder(503, new byte[0]));

        assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(5), Duration.ofSeconds(1)).baixar(CHAVE, pasta.resolve("a.pdf")))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("repositório de arquivos indisponível")
                .satisfies(e -> assertThat(((FalhaNoDocumentoException) e).tentativas()).isEqualTo(1));
        assertThat(pedidos).hasSize(1);
        assertThat(esperas).isEmpty();
    }

    @Test
    void chaveInvalidaInformaZeroTentativas() {
        assertThatThrownBy(() -> adapter("bearer", Duration.ofSeconds(5)).baixar("0", pasta.resolve("a.pdf")))
                .satisfies(e -> assertThat(((FalhaNoDocumentoException) e).tentativas()).isZero());
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
                limites(5), esperas::add);

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

    // --- token por arquivo (S2.2, bloco 4)

    private static final String TOKEN_DO_ARQUIVO_ANTIGO = "TOKEN-ARQUIVO-ANTIGO-DE-TESTE";
    private static final String TOKEN_DO_ARQUIVO_NOVO = "TOKEN-ARQUIVO-NOVO-DE-TESTE";

    private Path arquivoDoToken(String conteudo) throws IOException {
        Path arquivo = pasta.resolve("segredo").resolve("token-arquivos");
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        // Força outra data de modificação a cada troca (sistemas de arquivos com resolução grossa).
        Files.setLastModifiedTime(arquivo, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()
                + conteudo.hashCode()));
        return arquivo;
    }

    private ArquivosDoLicenciamentoHttp comArquivo(Path arquivo, ArquivosDoLicenciamentoHttp.Espera espera) {
        return new ArquivosDoLicenciamentoHttp(url(), FonteDoToken.de(TOKEN, arquivo.toString()), "bearer", "",
                limites(5), espera);
    }

    @Test
    void tokenDoArquivoTemPrecedenciaETrocaEntreDuasChamadasValeNaSegunda() throws IOException {
        rota(troca -> troca.responder(200, bytes("x")));
        Path arquivo = arquivoDoToken(TOKEN_DO_ARQUIVO_ANTIGO + "\n");
        ArquivosDoLicenciamentoHttp adapter = comArquivo(arquivo, esperas::add);

        adapter.baixar(CHAVE, pasta.resolve("a.pdf"));
        assertThat(ultima.get().cabecalho("Authorization")).isEqualTo("Bearer " + TOKEN_DO_ARQUIVO_ANTIGO);

        arquivoDoToken(TOKEN_DO_ARQUIVO_NOVO);
        adapter.baixar(CHAVE, pasta.resolve("b.pdf"));
        assertThat(ultima.get().cabecalho("Authorization")).isEqualTo("Bearer " + TOKEN_DO_ARQUIVO_NOVO);
    }

    @Test
    void springLeLicenciamentoApiTokenFileEDaPrecedenciaAoArquivo() throws IOException {
        rota(troca -> troca.responder(200, bytes("x")));
        Path arquivo = arquivoDoToken(TOKEN_DO_ARQUIVO_NOVO);
        ParametrosDoDossie parametros = org.mockito.Mockito.mock(ParametrosDoDossie.class);
        org.mockito.Mockito.when(parametros.downloadTimeoutSegundos()).thenReturn(5);
        org.mockito.Mockito.when(parametros.downloadTotalMaximoSegundos()).thenReturn(60);

        new ApplicationContextRunner()
                .withPropertyValues("rastro.arquivos.url=" + url(), "rastro.arquivos.token=" + TOKEN,
                        "rastro.arquivos.token-file=" + arquivo)
                .withBean(ParametrosDoDossie.class, () -> parametros)
                .withUserConfiguration(ArquivosDoLicenciamentoHttp.class)
                .run(contexto -> {
                    contexto.getBean(ArquivosDoLicenciamentoHttp.class).baixar(CHAVE, pasta.resolve("a.pdf"));
                    assertThat(ultima.get().cabecalho("Authorization")).isEqualTo("Bearer " + TOKEN_DO_ARQUIVO_NOVO);
                });
    }

    @Test
    void tokenTrocadoDuranteAEsperaValeNaNovaTentativa() throws IOException {
        List<String> autorizacoes = new CopyOnWriteArrayList<>();
        rota(troca -> {
            autorizacoes.add(troca.cabecalho("Authorization"));
            if (autorizacoes.size() == 1) {
                troca.responder(503, new byte[0]);
            } else {
                troca.responder(200, bytes("x"));
            }
        });
        Path arquivo = arquivoDoToken(TOKEN_DO_ARQUIVO_ANTIGO);

        int tentativas = comArquivo(arquivo, duracao -> {
            try {
                arquivoDoToken(TOKEN_DO_ARQUIVO_NOVO);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }).baixar(CHAVE, pasta.resolve("a.pdf"));

        assertThat(tentativas).isEqualTo(2);
        assertThat(autorizacoes).containsExactly("Bearer " + TOKEN_DO_ARQUIVO_ANTIGO, "Bearer " + TOKEN_DO_ARQUIVO_NOVO);
    }

    @Test
    void arquivoDoTokenAusenteOuVazioNaoChamaAApiENaoUsaATokenDaVariavel() throws IOException {
        rota(troca -> troca.responder(200, bytes("x")));
        Path vazio = arquivoDoToken("");
        Path ausente = pasta.resolve("nao-existe");

        for (Path arquivo : List.of(vazio, ausente)) {
            assertThatThrownBy(() -> comArquivo(arquivo, esperas::add).baixar(CHAVE, pasta.resolve("a.pdf")))
                    .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("API de arquivos não configurada")
                    .satisfies(e -> assertThat(((FalhaNoDocumentoException) e).tentativas()).isZero());
        }
        assertThat(pedidos).isEmpty();
    }

    @Test
    void tokenDoArquivoNaoApareceEmNenhumLogNemErroDeNenhumCenario() throws IOException {
        ListAppender<ILoggingEvent> raiz = new ListAppender<>();
        raiz.start();
        Logger logRaiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logRaiz.addAppender(raiz);
        List<String> saidas = new CopyOnWriteArrayList<>();
        Path arquivo = arquivoDoToken(TOKEN_DO_ARQUIVO_NOVO + "\n");
        AtomicInteger chamada = new AtomicInteger();
        List<ServidorHttpDeTeste.Tratador> cenarios = List.of(
                troca -> troca.responder(200, bytes("x")),
                troca -> troca.responder(chamada.incrementAndGet() % 2 == 1 ? 503 : 200, bytes("x")),
                troca -> troca.responder(401, new byte[0]),
                troca -> troca.responder(403, new byte[0]),
                troca -> troca.responder(404, new byte[0]),
                troca -> troca.responder(418, new byte[0]),
                troca -> troca.responder(500, new byte[0]),
                troca -> {
                    dormir(1500);
                    troca.responder(200, bytes("x"));
                });
        try {
            for (ServidorHttpDeTeste.Tratador cenario : cenarios) {
                rota(cenario);
                ArquivosDoLicenciamentoHttp adapter = new ArquivosDoLicenciamentoHttp(url(),
                        FonteDoToken.de(TOKEN, arquivo.toString()), "bearer", "",
                        limites(Duration.ofMillis(500), Duration.ofMinutes(1)), esperas::add);
                try {
                    saidas.add("tentativas=" + adapter.baixar(CHAVE, pasta.resolve("a.pdf")));
                } catch (RuntimeException e) {
                    for (Throwable t = e; t != null; t = t.getCause()) {
                        saidas.add(String.valueOf(t));
                    }
                }
            }
            // e o arquivo vazio
            arquivoDoToken("");
            try {
                comArquivo(arquivo, esperas::add).baixar(CHAVE, pasta.resolve("a.pdf"));
            } catch (RuntimeException e) {
                saidas.add(String.valueOf(e));
            }
        } finally {
            logRaiz.detachAppender(raiz);
        }
        raiz.list.forEach(evento -> {
            saidas.add(evento.getFormattedMessage());
            if (evento.getThrowableProxy() != null) {
                saidas.add(evento.getThrowableProxy().getMessage());
            }
        });
        log.list.forEach(evento -> saidas.add(evento.getFormattedMessage()));

        assertThat(pedidos).as("os cenários chegaram ao servidor com o token do arquivo").isNotEmpty();
        assertThat(saidas).isNotEmpty().allSatisfy(saida -> assertThat(String.valueOf(saida))
                .doesNotContain(TOKEN_DO_ARQUIVO_NOVO, TOKEN, CHAVE, url(), arquivo.toString(), "token-arquivos"));
    }

    // --- API não configurada

    @Test
    void semUrlADocumentoFalhaComMotivoExplicito() {
        ArquivosNaoConfigurados nao = new ArquivosNaoConfigurados();

        assertThatThrownBy(() -> nao.baixar(CHAVE, pasta.resolve("a.pdf")))
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("API de arquivos não configurada");
    }
}
