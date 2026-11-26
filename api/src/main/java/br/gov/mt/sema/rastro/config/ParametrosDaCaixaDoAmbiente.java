package br.gov.mt.sema.rastro.config;

import br.gov.mt.sema.rastro.caixa.application.ParametrosDaCaixa;
import br.gov.mt.sema.rastro.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import br.gov.mt.sema.rastro.painel.application.ParametrosDoPainel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parâmetros da caixa (S11) vindos do ambiente ({@code rastro.caixa.*}, variáveis {@code RASTRO_CAIXA_*}); valor fora da
 * faixa cai no padrão, com aviso no log. A tela de Configurações (classe "imediato") sobrepõe cada um e o valor é lido a cada
 * ciclo ({@link ParametrosEmVigor}), então vale na próxima atualização. O teto de requerimentos e o tempo total são os do
 * painel (nenhum parâmetro novo para eles).
 */
@Component
class ParametrosDaCaixaDoAmbiente implements ParametrosDaCaixa {

    private static final Logger log = LoggerFactory.getLogger(ParametrosDaCaixaDoAmbiente.class);

    private final int atualizacaoMinutos;
    private final int intervaloMinimoManualSegundos;
    private final ParametrosDoPainel painel;
    private final ParametrosEmVigor vigentes;

    ParametrosDaCaixaDoAmbiente(
            @Value("${rastro.caixa.atualizacao-minutos:10}") int atualizacaoMinutos,
            @Value("${rastro.caixa.intervalo-minimo-manual-segundos:60}") int intervaloMinimoManualSegundos,
            ParametrosDoPainel painel, ParametrosEmVigor vigentes) {
        this.painel = painel;
        this.vigentes = vigentes;
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
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_CAIXA_ATUALIZACAO, atualizacaoMinutos);
    }

    @Override
    public int intervaloMinimoManualSegundos() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_CAIXA_INTERVALO_MANUAL, intervaloMinimoManualSegundos);
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
