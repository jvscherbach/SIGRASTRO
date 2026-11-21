package br.gov.mt.sema.rastro.config;

import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parâmetros da tramitação vindos de variáveis de ambiente (S7, bloco 4). Valor fora da faixa cai no padrão, com aviso
 * no log. O bloco 10 acrescenta a sobreposição pela tela (ParametrosEmVigor), como os demais.
 */
@Component
class ParametrosDoRastroDoAmbiente implements ParametrosDoRastro {

    private static final Logger log = LoggerFactory.getLogger(ParametrosDoRastroDoAmbiente.class);

    static final int MAXIMO_DE_NOMES_PADRAO = 10;
    static final int CACHE_PADRAO = 30;
    static final int CACHE_MAXIMO = 3_600;

    private final boolean mostrarNome;
    private final int maximoDeNomes;
    private final int cacheSegundos;

    ParametrosDoRastroDoAmbiente(
            @Value("${rastro.tramitacao.mostrar-nome-responsavel:true}") boolean mostrarNome,
            @Value("${rastro.tramitacao.max-nomes-por-equipe:10}") int maximoDeNomes,
            @Value("${rastro.tramitacao.posicao-cache-segundos:30}") int cacheSegundos) {
        this.mostrarNome = mostrarNome;
        this.maximoDeNomes = faixa("RASTRO_MAX_NOMES_POR_EQUIPE", maximoDeNomes, 1, 100, MAXIMO_DE_NOMES_PADRAO);
        this.cacheSegundos = faixa("RASTRO_POSICAO_CACHE_SEGUNDOS", cacheSegundos, 0, CACHE_MAXIMO, CACHE_PADRAO);
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
        return mostrarNome;
    }

    @Override
    public int maximoDeNomesPorEquipe() {
        return maximoDeNomes;
    }

    @Override
    public int cacheDaPosicaoSegundos() {
        return cacheSegundos;
    }
}
