package br.gov.mt.sema.rastro.config;

import br.gov.mt.sema.rastro.caixa.application.ParametrosDaCaixa;
import br.gov.mt.sema.rastro.painel.application.ParametrosDoPainel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parâmetros da caixa (S11) vindos do ambiente ({@code rastro.caixa.*}, variáveis {@code RASTRO_CAIXA_*}); valor fora da
 * faixa cai no padrão, com aviso no log. O teto de requerimentos e o tempo total são os do painel (nenhum parâmetro novo
 * para eles). A tela de Configurações e o catálogo de parâmetros entram no bloco 6.
 */
@Component
class ParametrosDaCaixaDoAmbiente implements ParametrosDaCaixa {

    private static final Logger log = LoggerFactory.getLogger(ParametrosDaCaixaDoAmbiente.class);

    private final int atualizacaoMinutos;
    private final int intervaloMinimoManualSegundos;
    private final ParametrosDoPainel painel;

    ParametrosDaCaixaDoAmbiente(
            @Value("${rastro.caixa.atualizacao-minutos:10}") int atualizacaoMinutos,
            @Value("${rastro.caixa.intervalo-minimo-manual-segundos:60}") int intervaloMinimoManualSegundos,
            ParametrosDoPainel painel) {
        this.painel = painel;
        this.atualizacaoMinutos = faixa("RASTRO_CAIXA_ATUALIZACAO_MINUTOS", atualizacaoMinutos, 2, 120, 10);
        this.intervaloMinimoManualSegundos = faixa("RASTRO_CAIXA_INTERVALO_MINIMO_MANUAL_SEGUNDOS",
                intervaloMinimoManualSegundos, 15, 3600, 60);
    }

    private static int faixa(String variavel, int valor, int minimo, int maximo, int padrao) {
        if (valor >= minimo && valor <= maximo) {
            return valor;
        }
        log.warn("{} fora da faixa; usando {}", variavel, padrao);
        return padrao;
    }

    @Override
    public int atualizacaoMinutos() {
        return atualizacaoMinutos;
    }

    @Override
    public int intervaloMinimoManualSegundos() {
        return intervaloMinimoManualSegundos;
    }

    @Override
    public int limiteRequerimentos() {
        return painel.limiteRequerimentos();
    }

    @Override
    public int timeoutSegundos() {
        return painel.timeoutSegundos();
    }
}
