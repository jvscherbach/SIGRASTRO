package br.gov.mt.sema.rastro.integration.licenciamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Classificacao;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Resultado;
import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.rastro.testsupport.ServidorHttpDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Teste de conexão da API de arquivos (S6, bloco 4), só contra um servidor HTTP local de teste: cada classificação, 503
 * isolado e persistente, tempo esgotado, DNS inexistente, TLS inválido, e que o teste usa a mesma URL e a mesma
 * autenticação do download real. Token e chave são fictícios.
 */
class VerificadorDaApiDeArquivosTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);
    private static final String TOKEN = "token-de-teste";

    @TempDir
    Path pasta;

    private ServidorHttpDeTeste servidor;
    private final List<Duration> esperas = new CopyOnWriteArrayList<>();

    @BeforeEach
    void iniciar() throws IOException {
        servidor = ServidorHttpDeTeste.iniciar();
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    /** Token fictício, com assinatura sem sentido: ela não é conferida. */
    private static String jwt(Instant expiracao) {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String corpo = base64.encodeToString(("{\"exp\":" + expiracao.getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
        return base64.encodeToString("{}".getBytes(StandardCharsets.UTF_8)) + "." + corpo + ".assinatura";
    }

    private VerificadorDaApiDeArquivos verificador(String url, String token, String modo, String cookie, Duration limite) {
        return new VerificadorDaApiDeArquivos(url, FonteDoToken.de(token, null), modo, cookie, RELOGIO, limite,
                esperas::add);
    }

    private VerificadorDaApiDeArquivos verificador(String token) {
        return verificador(servidor.url() + "/", token, "bearer", "", Duration.ofSeconds(5));
    }

    private Resultado comStatus(int status) {
        servidor.responderCom(troca -> troca.responder(status, "{\"erro\":\"corpo do servico\"}".getBytes()));
        return verificador(TOKEN).verificar();
    }

    private static void semSegredo(Resultado resultado, String url) {
        assertThat(resultado.mensagem()).doesNotContain(TOKEN).doesNotContain(url).doesNotContain("127.0.0.1")
                .doesNotContain("corpo do servico").doesNotContain(VerificadorDaApiDeArquivos.CHAVE_FICTICIA);
    }

    @Test
    void resposta_2xx_e_ok() {
        Resultado resultado = comStatus(200);

        assertThat(resultado.classificacao()).isEqualTo(Classificacao.OK);
        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.codigoHttp()).isEqualTo(200);
        semSegredo(resultado, servidor.url());
    }

    @Test
    void quatrocentos_e_quatrocentos_e_quatro_para_chave_ficticia_sao_alcancavel_com_ressalva() {
        for (int status : new int[] {400, 404}) {
            Resultado resultado = comStatus(status);

            assertThat(resultado.classificacao()).as("HTTP " + status).isEqualTo(Classificacao.ALCANCAVEL_COM_RESSALVA);
            assertThat(resultado.sucesso()).isTrue();
            assertThat(resultado.codigoHttp()).isEqualTo(status);
            assertThat(resultado.mensagem()).contains("HTTP " + status).contains("chave fictícia");
            semSegredo(resultado, servidor.url());
        }
    }

    @Test
    void quatrocentos_e_um_e_tres_sao_falha_de_autenticacao_e_orientam_renovar_o_token() {
        for (int status : new int[] {401, 403}) {
            Resultado resultado = comStatus(status);

            assertThat(resultado.classificacao()).isEqualTo(Classificacao.FALHA_DE_AUTENTICACAO);
            assertThat(resultado.sucesso()).isFalse();
            assertThat(resultado.mensagem()).contains("HTTP " + status).contains("Renove o token");
        }
    }

    @Test
    void cinco_zero_tres_isolado_ganha_uma_nova_tentativa_e_nao_e_falso_alarme() {
        AtomicInteger chamadas = new AtomicInteger();
        servidor.responderCom(troca -> troca.responder(chamadas.incrementAndGet() == 1 ? 503 : 404, new byte[0]));

        Resultado resultado = verificador(TOKEN).verificar();

        assertThat(chamadas).hasValue(2);
        assertThat(esperas).containsExactly(ArquivosDoLicenciamentoHttp.ESPERAS.get(0));
        assertThat(resultado.classificacao()).isEqualTo(Classificacao.ALCANCAVEL_COM_RESSALVA);
    }

    @Test
    void cinco_zero_tres_persistente_e_falha_com_o_codigo() {
        AtomicInteger chamadas = new AtomicInteger();
        servidor.responderCom(troca -> {
            chamadas.incrementAndGet();
            troca.responder(503, new byte[0]);
        });

        Resultado resultado = verificador(TOKEN).verificar();

        assertThat(chamadas).as("uma nova tentativa só").hasValue(2);
        assertThat(resultado.classificacao()).isEqualTo(Classificacao.FALHA);
        assertThat(resultado.codigoHttp()).isEqualTo(503);
        assertThat(resultado.mensagem()).contains("HTTP 503 duas vezes");
    }

    @Test
    void outro_cinco_xx_e_falha_sem_nova_tentativa() {
        AtomicInteger chamadas = new AtomicInteger();
        servidor.responderCom(troca -> {
            chamadas.incrementAndGet();
            troca.responder(500, new byte[0]);
        });

        Resultado resultado = verificador(TOKEN).verificar();

        assertThat(chamadas).hasValue(1);
        assertThat(resultado.classificacao()).isEqualTo(Classificacao.FALHA);
        assertThat(resultado.codigoHttp()).isEqualTo(500);
    }

    @Test
    void tempo_esgotado_usa_o_tempo_limite_configurado_e_o_informa() {
        servidor.responderCom(troca -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            troca.responder(404, new byte[0]);
        });

        Resultado resultado = verificador(servidor.url(), TOKEN, "bearer", "", Duration.ofSeconds(1)).verificar();

        assertThat(resultado.classificacao()).isEqualTo(Classificacao.FALHA);
        assertThat(resultado.codigoHttp()).isNull();
        assertThat(resultado.mensagem()).isEqualTo("Tempo limite do teste (1 s) excedido sem resposta.");
    }

    @Test
    void servico_fora_do_ar_e_recusa_de_conexao_sem_mostrar_url() throws IOException {
        int porta;
        try (ServerSocket livre = new ServerSocket(0)) {
            porta = livre.getLocalPort();
        }
        String url = "http://127.0.0.1:" + porta;

        Resultado resultado = verificador(url, TOKEN, "bearer", "", Duration.ofSeconds(2)).verificar();

        assertThat(resultado.classificacao()).isEqualTo(Classificacao.FALHA);
        assertThat(resultado.mensagem()).isEqualTo("Conexão recusada ou sem rota até o serviço.");
        semSegredo(resultado, url);
    }

    @Test
    void dns_inexistente_e_falha_de_dns() {
        // ".invalid" é reservado (RFC 2606): nunca resolve, nenhum serviço real é tocado.
        String url = "http://servico-de-teste.invalid";

        Resultado resultado = verificador(url, TOKEN, "bearer", "", Duration.ofSeconds(2)).verificar();

        assertThat(resultado.classificacao()).isEqualTo(Classificacao.FALHA);
        assertThat(resultado.mensagem()).contains("DNS").doesNotContain("servico-de-teste");
    }

    @Test
    void tls_invalido_e_falha_de_conexao_segura() throws Exception {
        // HTTPS contra um soquete local que responde em texto puro assim que conecta: a negociação TLS falha.
        try (ServerSocket semTls = new ServerSocket(0)) {
            Thread atendente = Thread.ofVirtual().start(() -> {
                try (java.net.Socket conexao = semTls.accept()) {
                    conexao.getOutputStream().write("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                    conexao.getOutputStream().flush();
                    Thread.sleep(500);
                } catch (IOException | InterruptedException ignorada) {
                    // o cliente desiste da negociação
                }
            });
            String url = "https://127.0.0.1:" + semTls.getLocalPort();

            Resultado resultado = verificador(url, TOKEN, "bearer", "", Duration.ofSeconds(2)).verificar();

            assertThat(resultado.classificacao()).isEqualTo(Classificacao.FALHA);
            assertThat(resultado.mensagem()).isEqualTo("Falha na conexão segura (TLS/certificado).");
            atendente.join(2_000);
        }
    }

    @Test
    void usa_a_mesma_url_e_a_mesma_autenticacao_do_download_real() throws IOException {
        for (String modo : new String[] {"bearer", "cookie"}) {
            List<String> pedidos = new ArrayList<>();
            servidor.responderCom(troca -> {
                pedidos.add(troca.metodo() + " " + troca.caminhoBruto() + " | " + troca.cabecalho("Authorization")
                        + " | " + troca.cabecalho("Cookie"));
                troca.responder(404, new byte[0]);
            });
            String base = servidor.url() + "/api-base/";
            ArquivosDoLicenciamentoHttp real = new ArquivosDoLicenciamentoHttp(base, TOKEN, modo, "sessao",
                    () -> new LimitesDeDownload(Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(5)),
                    duracao -> { });
            Path destino = Files.createTempFile(pasta, "arquivo", ".bin");
            assertThatThrownBy(() -> real.baixar(VerificadorDaApiDeArquivos.CHAVE_FICTICIA, destino))
                    .isInstanceOf(FalhaNoDocumentoException.class);

            verificador(base, TOKEN, modo, "sessao", Duration.ofSeconds(2)).verificar();

            assertThat(pedidos).as(modo).hasSize(2);
            assertThat(pedidos.get(1)).as("teste igual ao fluxo real, só muda a chave (aqui, a mesma)")
                    .isEqualTo(pedidos.get(0));
            assertThat(pedidos.get(0)).startsWith("GET /api-base/api/arquivo/verificacao-de-conexao");
        }
    }

    @Test
    void horas_ate_expirar_so_pelo_campo_exp() {
        assertThat(verificador(jwt(AGORA.plus(Duration.ofHours(30)))).horasAteExpirar()).contains(30L);
        assertThat(verificador("nao-e-jwt").horasAteExpirar()).isEmpty();
        assertThat(verificador("a.b.c").horasAteExpirar()).isEmpty();
    }

    @Test
    void token_vencido_nem_vai_ao_servico_e_e_falha_de_autenticacao() {
        AtomicInteger chamadas = new AtomicInteger();
        servidor.responderCom(troca -> {
            chamadas.incrementAndGet();
            troca.responder(200, new byte[0]);
        });
        VerificadorDaApiDeArquivos verificador = verificador(jwt(AGORA.minus(Duration.ofHours(2))));

        Resultado resultado = verificador.verificar();

        assertThat(resultado.classificacao()).isEqualTo(Classificacao.FALHA_DE_AUTENTICACAO);
        assertThat(resultado.mensagem()).startsWith("Token vencido");
        assertThat(verificador.horasAteExpirar()).contains(-2L);
        assertThat(chamadas).hasValue(0);
    }

    @Test
    void sem_url_nao_esta_configurada() {
        assertThat(verificador(" ", "t", "bearer", "", Duration.ofSeconds(1)).estado())
                .isEqualTo(VerificadorDeConexao.Estado.NAO_CONFIGURADA);
    }
}
