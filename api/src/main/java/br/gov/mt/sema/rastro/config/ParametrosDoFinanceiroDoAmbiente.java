package br.gov.mt.sema.rastro.config;

import br.gov.mt.sema.rastro.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import br.gov.mt.sema.rastro.financeiro.application.port.ParametrosDoFinanceiro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parâmetros da leitura do financeiro (S9). O padrão vem das variáveis de ambiente (valor fora da faixa cai no padrão, com
 * aviso no log); desde o bloco 10, a tela de Configurações (aba Rastro) sobrepõe cada um e o valor é lido a cada consulta,
 * então vale na próxima leitura (classe "imediato"), como os demais parâmetros ({@link ParametrosEmVigor}).
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
    private final boolean mostrarValores;
    private final ParametrosEmVigor vigentes;

    ParametrosDoFinanceiroDoAmbiente(
            @Value("${rastro.financeiro.limite-de-lancamentos:" + LIMITE_PADRAO + "}") int limiteDeLancamentos,
            @Value("${rastro.financeiro.timeout-segundos:" + TIMEOUT_PADRAO + "}") int consultaTimeoutSegundos,
            @Value("${rastro.financeiro.mostrar-valores:true}") boolean mostrarValores,
            ParametrosEmVigor vigentes) {
        this.mostrarValores = mostrarValores;
        this.limiteDeLancamentos = faixa("RASTRO_FINANCEIRO_LIMITE_LANCAMENTOS", limiteDeLancamentos, 1, LIMITE_MAXIMO,
                LIMITE_PADRAO);
        this.consultaTimeoutSegundos = faixa("RASTRO_FINANCEIRO_TIMEOUT_SEGUNDOS", consultaTimeoutSegundos, 1,
                TIMEOUT_MAXIMO, TIMEOUT_PADRAO);
        this.vigentes = vigentes;
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
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_FINANCEIRO_LIMITE, limiteDeLancamentos);
    }

    @Override
    public int consultaTimeoutSegundos() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_FINANCEIRO_TIMEOUT, consultaTimeoutSegundos);
    }

    @Override
    public boolean mostrarValores() {
        return vigentes.booleano(CatalogoDeParametros.RASTRO_MOSTRAR_VALORES, mostrarValores);
    }
}
