package br.gov.mt.sema.rastro.config;

import br.gov.mt.sema.rastro.financeiro.application.port.ParametrosDoFinanceiro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parâmetros da leitura do financeiro (S9, bloco 2), por enquanto só do ambiente; valor fora da faixa cai no padrão, com
 * aviso no log. O bloco 10 os leva para a tela de Configurações.
 */
@Component
class ParametrosDoFinanceiroDoAmbiente implements ParametrosDoFinanceiro {

    private static final Logger log = LoggerFactory.getLogger(ParametrosDoFinanceiroDoAmbiente.class);

    static final int LIMITE_PADRAO = 200;
    static final int LIMITE_MAXIMO = 1_000;
    static final int TIMEOUT_PADRAO = 10;
    static final int TIMEOUT_MAXIMO = 120;

    private final int limiteDeLancamentos;
    private final int consultaTimeoutSegundos;

    ParametrosDoFinanceiroDoAmbiente(
            @Value("${rastro.financeiro.limite-de-lancamentos:" + LIMITE_PADRAO + "}") int limiteDeLancamentos,
            @Value("${rastro.financeiro.timeout-segundos:" + TIMEOUT_PADRAO + "}") int consultaTimeoutSegundos) {
        this.limiteDeLancamentos = faixa("RASTRO_FINANCEIRO_LIMITE_LANCAMENTOS", limiteDeLancamentos, 1, LIMITE_MAXIMO,
                LIMITE_PADRAO);
        this.consultaTimeoutSegundos = faixa("RASTRO_FINANCEIRO_TIMEOUT_SEGUNDOS", consultaTimeoutSegundos, 1,
                TIMEOUT_MAXIMO, TIMEOUT_PADRAO);
    }

    private static int faixa(String variavel, int valor, int minimo, int maximo, int padrao) {
        if (valor >= minimo && valor <= maximo) {
            return valor;
        }
        log.warn("{} fora de {} a {}; usando {}", variavel, minimo, maximo, padrao);
        return padrao;
    }

    @Override
    public int limiteDeLancamentos() {
        return limiteDeLancamentos;
    }

    @Override
    public int consultaTimeoutSegundos() {
        return consultaTimeoutSegundos;
    }
}
