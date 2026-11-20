package br.gov.mt.sema.rastro.integration.scorp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Classificacao;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Estado;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Resultado;
import br.gov.mt.sema.rastro.testsupport.ServidorHttpDeTeste;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Teste de conexão do SCORP (S6, bloco 4), só contra um servidor HTTP local de teste. O 400 que o SCORP devolve para o
 * identificador fictício era tratado como falha (falso alarme); agora é "alcançável com ressalva".
 */
class VerificadorDoScorpTest {

    private ServidorHttpDeTeste servidor;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = ServidorHttpDeTeste.iniciar();
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    private VerificadorDoScorp verificador(String url, String modo) {
        return new VerificadorDoScorp(url, origem -> true, CredencialDoScorp.de("token-de-teste", null, null), modo,
                "sessao", Duration.ofSeconds(2), duracao -> { });
    }

    private Resultado comStatus(int status) {
        servidor.responderCom(troca -> troca.responder(status, new byte[0]));
        return verificador(servidor.url(), "bearer").verificar();
    }

    @Test
    void classificacao_de_cada_resposta() {
        assertThat(comStatus(200).classificacao()).isEqualTo(Classificacao.OK);
        Resultado quatrocentos = comStatus(400);
        assertThat(quatrocentos.classificacao()).isEqualTo(Classificacao.ALCANCAVEL_COM_RESSALVA);
        assertThat(quatrocentos.sucesso()).isTrue();
        assertThat(quatrocentos.mensagem()).contains("HTTP 400").contains("identificador de pessoa fictício")
                .doesNotContain("token-de-teste").doesNotContain(servidor.url());
        assertThat(comStatus(404).classificacao()).isEqualTo(Classificacao.ALCANCAVEL_COM_RESSALVA);
        assertThat(comStatus(401).classificacao()).isEqualTo(Classificacao.FALHA_DE_AUTENTICACAO);
        assertThat(comStatus(403).classificacao()).isEqualTo(Classificacao.FALHA_DE_AUTENTICACAO);
        assertThat(comStatus(500).classificacao()).isEqualTo(Classificacao.FALHA);
    }

    @Test
    void cinco_zero_tres_isolado_tenta_de_novo_e_persistente_falha() {
        AtomicInteger chamadas = new AtomicInteger();
        servidor.responderCom(troca -> troca.responder(chamadas.incrementAndGet() == 1 ? 503 : 400, new byte[0]));
        assertThat(verificador(servidor.url(), "bearer").verificar().classificacao())
                .isEqualTo(Classificacao.ALCANCAVEL_COM_RESSALVA);
        assertThat(chamadas).hasValue(2);

        Resultado persistente = comStatus(503);
        assertThat(persistente.classificacao()).isEqualTo(Classificacao.FALHA);
        assertThat(persistente.codigoHttp()).isEqualTo(503);
    }

    @Test
    void usa_a_mesma_url_e_a_mesma_credencial_da_consulta_real() {
        for (String modo : new String[] {"bearer", "cookie"}) {
            List<String> pedidos = new ArrayList<>();
            servidor.responderCom(troca -> {
                pedidos.add(troca.metodo() + " " + troca.caminhoBruto() + " | " + troca.cabecalho("Authorization")
                        + " | " + troca.cabecalho("Cookie"));
                troca.responder(400, new byte[0]);
            });
            String base = servidor.url() + "/api/";
            DocumentosDoScorpHttp real = new DocumentosDoScorpHttp(base, true,
                    CredencialDoScorp.de("token-de-teste", null, null), modo, "sessao", Duration.ofSeconds(2), 1,
                    new ObjectMapper(), duracao -> { });

            assertThat(real.consultar(Set.of(VerificadorDoScorp.IDENTIFICADOR)).falharam()).hasSize(1);
            verificador(base, modo).verificar();

            assertThat(pedidos).as(modo).hasSize(2);
            assertThat(pedidos.get(1)).as("teste igual à consulta real, só muda o identificador (aqui, o mesmo)")
                    .isEqualTo(pedidos.get(0));
            assertThat(pedidos.get(0)).startsWith("GET /api/documentos/pessoa-licenciamento/verificacao-de-conexao");
        }
    }

    @Test
    void estado() {
        assertThat(verificador(servidor.url(), "bearer").estado()).isEqualTo(Estado.CONFIGURADA);
        assertThat(verificador(" ", "bearer").estado()).isEqualTo(Estado.NAO_CONFIGURADA);
        assertThat(new VerificadorDoScorp(servidor.url(), origem -> false, CredencialDoScorp.de("t", null, null), "bearer",
                "", Duration.ofSeconds(1), duracao -> { }).estado()).isEqualTo(Estado.DESATIVADA);
    }
}
