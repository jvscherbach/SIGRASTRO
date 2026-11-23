package br.gov.mt.sema.rastro.integration.siga;

import java.util.concurrent.TimeUnit;

import br.gov.mt.sema.rastro.painel.application.AtualizarPainel;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Base dos testes do painel contra o Oracle de teste (S10, bloco 6): o serviço inteiro com o ano fixo em 2026, a população
 * PN01 a PN16 gravada (sob demanda) e ajudantes para esperar a atualização em segundo plano.
 */
@TestPropertySource(properties = "rastro.painel.ano=2026")
abstract class BaseDoPainelHttp extends BaseVarreduraDaTramitacao {

    @Autowired
    AtualizarPainel atualizador;

    @BeforeAll
    static void gravarPopulacaoDoPainel() {
        org.assertj.core.api.Assertions.assertThat(SigaOracleTeste.cenariosDoPainel()).isNotEmpty();
    }

    /** Dispara a atualização (sem passar pelo 429) e espera terminar. */
    void atualizarEEsperar() throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (!atualizador.iniciarEmSegundoPlano()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("outra atualização não terminou");
            }
            Thread.sleep(100);
        }
        esperarTerminar();
    }

    void esperarTerminar() throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (atualizador.emAndamento()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("a atualização não terminou");
            }
            Thread.sleep(100);
        }
    }

    JsonNode painel(String usuario) throws Exception {
        return json.readTree(corpo(obter(usuario, "/api/painel")));
    }
}
