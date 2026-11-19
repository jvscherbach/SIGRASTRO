package br.gov.mt.sema.rastro.integration.licenciamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Resultado;
import br.gov.mt.sema.rastro.testsupport.ServidorHttpDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerificadorDaApiDeArquivosTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    private ServidorHttpDeTeste servidor;

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

    private VerificadorDaApiDeArquivos verificador(String token) {
        return new VerificadorDaApiDeArquivos(servidor.url() + "/", FonteDoToken.de(token, null), "bearer", "", RELOGIO);
    }

    private Resultado comStatus(int status) {
        servidor.responderCom(troca -> troca.responder(status, new byte[0]));
        return verificador("token-de-teste").verificar();
    }

    @Test
    void chave_inexistente_com_404_prova_que_responde() {
        AtomicReference<String> caminho = new AtomicReference<>();
        AtomicReference<String> autorizacao = new AtomicReference<>();
        servidor.responderCom(troca -> {
            caminho.set(troca.caminhoBruto());
            autorizacao.set(troca.cabecalho("Authorization"));
            troca.responder(404, new byte[0]);
        });

        Resultado resultado = verificador("token-de-teste").verificar();

        assertThat(resultado.sucesso()).isTrue();
        assertThat(caminho.get()).isEqualTo(VerificadorDaApiDeArquivos.CAMINHO);
        assertThat(autorizacao.get()).isEqualTo("Bearer token-de-teste");
    }

    @Test
    void token_recusado_e_falha_do_servico() {
        assertThat(comStatus(401)).isEqualTo(new Resultado(false, "Token recusado."));
        assertThat(comStatus(403)).isEqualTo(new Resultado(false, "Token recusado."));
        assertThat(comStatus(503)).isEqualTo(new Resultado(false, "Serviço respondeu HTTP 503."));
    }

    @Test
    void servico_fora_do_ar_nao_mostra_url() throws IOException {
        int porta;
        try (ServerSocket livre = new ServerSocket(0)) {
            porta = livre.getLocalPort();
        }

        Resultado resultado = new VerificadorDaApiDeArquivos("http://127.0.0.1:" + porta, FonteDoToken.de("t", null), "bearer",
                "", RELOGIO).verificar();

        assertThat(resultado).isEqualTo(new Resultado(false, "Serviço inacessível."));
    }

    @Test
    void horas_ate_expirar_so_pelo_campo_exp() {
        assertThat(verificador(jwt(AGORA.plus(Duration.ofHours(30)))).horasAteExpirar()).contains(30L);
        assertThat(verificador("nao-e-jwt").horasAteExpirar()).isEmpty();
        assertThat(verificador("a.b.c").horasAteExpirar()).isEmpty();
    }

    @Test
    void token_vencido_nem_vai_ao_servico() {
        AtomicInteger chamadas = new AtomicInteger();
        servidor.responderCom(troca -> {
            chamadas.incrementAndGet();
            troca.responder(200, new byte[0]);
        });
        VerificadorDaApiDeArquivos verificador = verificador(jwt(AGORA.minus(Duration.ofHours(2))));

        assertThat(verificador.verificar()).isEqualTo(new Resultado(false, "Token vencido."));
        assertThat(verificador.horasAteExpirar()).contains(-2L);
        assertThat(chamadas).hasValue(0);
    }

    @Test
    void sem_url_nao_esta_configurada() {
        assertThat(new VerificadorDaApiDeArquivos(" ", FonteDoToken.de("t", null), "bearer", "", RELOGIO).estado())
                .isEqualTo(VerificadorDeConexao.Estado.NAO_CONFIGURADA);
    }

    @Test
    void modo_cookie() {
        AtomicReference<String> cookie = new AtomicReference<>();
        servidor.responderCom(troca -> {
            cookie.set(troca.cabecalho("Cookie"));
            troca.responder(200, new byte[0]);
        });

        assertThat(new VerificadorDaApiDeArquivos(servidor.url(), FonteDoToken.de("valor", null), "cookie", "sessao", RELOGIO)
                .verificar().sucesso()).isTrue();
        assertThat(cookie.get()).isEqualTo("sessao=valor");
    }
}
