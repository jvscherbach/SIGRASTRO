package br.gov.mt.sema.rastro.config;

import br.gov.mt.sema.rastro.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parâmetros da tramitação (S7). O padrão vem das variáveis de ambiente (bloco 4; valor fora da faixa cai no padrão, com
 * aviso no log); desde o bloco 10, a tela de Configurações sobrepõe cada um (classe "imediato": lidos a cada consulta,
 * valem na próxima leitura), como os demais parâmetros ({@link ParametrosEmVigor}).
 */
@Component
class ParametrosDoRastroDoAmbiente implements ParametrosDoRastro {

    private static final Logger log = LoggerFactory.getLogger(ParametrosDoRastroDoAmbiente.class);

    static final int MAXIMO_DE_NOMES_PADRAO = 10;
    static final int CACHE_PADRAO = 30;
    static final int CACHE_MAXIMO = 3_600;
    static final int POSICOES_SIMULTANEAS_PADRAO = 4;
    static final int CAMUNDA_TIMEOUT_PADRAO = 10;
    static final int TRAMITACAO_CACHE_PADRAO = 60;
    static final int TRAMITACAO_TIMEOUT_PADRAO = 20;
    static final int LEITURAS_SIMULTANEAS_PADRAO = 4;

    private final int tramitacaoCacheSegundos;
    private final int tramitacaoTimeoutSegundos;
    private final int leiturasSimultaneas;

    private final boolean mostrarNome;
    private final int maximoDeNomes;
    private final int cacheSegundos;
    private final int posicoesSimultaneas;
    private final int camundaTimeoutSegundos;
    private final ParametrosEmVigor vigentes;

    ParametrosDoRastroDoAmbiente(
            @Value("${rastro.tramitacao.mostrar-nome-responsavel:true}") boolean mostrarNome,
            @Value("${rastro.tramitacao.max-nomes-por-equipe:10}") int maximoDeNomes,
            @Value("${rastro.tramitacao.posicao-cache-segundos:30}") int cacheSegundos,
            @Value("${rastro.tramitacao.posicoes-simultaneas:4}") int posicoesSimultaneas,
            @Value("${rastro.tramitacao.camunda-timeout-segundos:10}") int camundaTimeoutSegundos,
            @Value("${rastro.tramitacao.tramitacao-cache-segundos:" + TRAMITACAO_CACHE_PADRAO + "}") int tramitacaoCache,
            @Value("${rastro.tramitacao.tramitacao-timeout-segundos:" + TRAMITACAO_TIMEOUT_PADRAO + "}")
            int tramitacaoTimeout,
            @Value("${rastro.tramitacao.camunda-leituras-simultaneas:" + LEITURAS_SIMULTANEAS_PADRAO + "}")
            int leiturasSimultaneas,
            ParametrosEmVigor vigentes) {
        // S8, bloco 7: só do ambiente (não estão na tela de Configurações); valem a partir da subida.
        this.tramitacaoCacheSegundos = faixa("RASTRO_TRAMITACAO_CACHE_SEGUNDOS", tramitacaoCache, 0, CACHE_MAXIMO,
                TRAMITACAO_CACHE_PADRAO);
        this.tramitacaoTimeoutSegundos = faixa("RASTRO_TRAMITACAO_TIMEOUT_SEGUNDOS", tramitacaoTimeout, 1, 120,
                TRAMITACAO_TIMEOUT_PADRAO);
        this.leiturasSimultaneas = faixa("RASTRO_CAMUNDA_LEITURAS_SIMULTANEAS", leiturasSimultaneas, 1, 20,
                LEITURAS_SIMULTANEAS_PADRAO);
        this.mostrarNome = mostrarNome;
        this.maximoDeNomes = faixa("RASTRO_MAX_NOMES_POR_EQUIPE", maximoDeNomes, 1, 100, MAXIMO_DE_NOMES_PADRAO);
        this.cacheSegundos = faixa("RASTRO_POSICAO_CACHE_SEGUNDOS", cacheSegundos, 0, CACHE_MAXIMO, CACHE_PADRAO);
        this.posicoesSimultaneas = faixa("RASTRO_POSICOES_SIMULTANEAS", posicoesSimultaneas, 1, 20,
                POSICOES_SIMULTANEAS_PADRAO);
        this.camundaTimeoutSegundos = faixa("RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS", camundaTimeoutSegundos, 1, 120,
                CAMUNDA_TIMEOUT_PADRAO);
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
    public boolean mostrarNomeDoResponsavel() {
        return vigentes.booleano(CatalogoDeParametros.RASTRO_MOSTRAR_NOME, mostrarNome);
    }

    @Override
    public int maximoDeNomesPorEquipe() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_MAX_NOMES, maximoDeNomes);
    }

    @Override
    public int cacheDaPosicaoSegundos() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_CACHE_POSICAO, cacheSegundos);
    }

    @Override
    public int posicoesSimultaneas() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_POSICOES_SIMULTANEAS, posicoesSimultaneas);
    }

    @Override
    public int cacheDaTramitacaoSegundos() {
        return tramitacaoCacheSegundos;
    }

    @Override
    public int tramitacaoTimeoutSegundos() {
        return tramitacaoTimeoutSegundos;
    }

    @Override
    public int leiturasSimultaneasDoCamunda() {
        return leiturasSimultaneas;
    }

    @Override
    public int camundaTimeoutSegundos() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_CAMUNDA_TIMEOUT, camundaTimeoutSegundos);
    }
}
