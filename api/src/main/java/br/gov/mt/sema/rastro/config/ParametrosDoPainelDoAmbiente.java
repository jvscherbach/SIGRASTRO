package br.gov.mt.sema.rastro.config;

import br.gov.mt.sema.rastro.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import br.gov.mt.sema.rastro.painel.application.ParametrosDoPainel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parâmetros do painel (S10) vindos do ambiente ({@code rastro.painel.*}, variáveis {@code RASTRO_PAINEL_*}); valor fora
 * da faixa cai no padrão, com aviso no log. A tela de Configurações (classe "imediato") sobrepõe cada um e o valor é lido a
 * cada ciclo ({@link ParametrosEmVigor}), então vale na próxima atualização.
 */
@Component
class ParametrosDoPainelDoAmbiente implements ParametrosDoPainel {

    private static final Logger log = LoggerFactory.getLogger(ParametrosDoPainelDoAmbiente.class);

    private final int ano;
    private final int atualizacaoMinutos;
    private final int limiteRequerimentos;
    private final int timeoutSegundos;
    private final int intervaloMinimoManualSegundos;
    private final ParametrosEmVigor vigentes;

    ParametrosDoPainelDoAmbiente(
            @Value("${rastro.painel.ano:0}") int ano,
            @Value("${rastro.painel.atualizacao-minutos:30}") int atualizacaoMinutos,
            @Value("${rastro.painel.limite-requerimentos:3000}") int limiteRequerimentos,
            @Value("${rastro.painel.timeout-segundos:60}") int timeoutSegundos,
            @Value("${rastro.painel.intervalo-minimo-manual-segundos:120}") int intervaloMinimoManualSegundos,
            ParametrosEmVigor vigentes) {
        this.vigentes = vigentes;
        this.ano = (ano == 0 || (ano >= 2000 && ano <= 2100)) ? ano : padrao("RASTRO_PAINEL_ANO", 0);
        this.atualizacaoMinutos = faixa("RASTRO_PAINEL_ATUALIZACAO_MINUTOS", atualizacaoMinutos, 5, 1440, 30);
        this.limiteRequerimentos = faixa("RASTRO_PAINEL_LIMITE_REQUERIMENTOS", limiteRequerimentos, 100, 20_000, 3000);
        this.timeoutSegundos = faixa("RASTRO_PAINEL_TIMEOUT_SEGUNDOS", timeoutSegundos, 10, 300, 60);
        this.intervaloMinimoManualSegundos = faixa("RASTRO_PAINEL_INTERVALO_MINIMO_MANUAL_SEGUNDOS",
                intervaloMinimoManualSegundos, 30, 3600, 120);
    }

    private static int padrao(String variavel, int padrao) {
        log.warn("{} fora da faixa; usando {}", variavel, padrao);
        return padrao;
    }

    private static int faixa(String variavel, int valor, int minimo, int maximo, int padrao) {
        return valor >= minimo && valor <= maximo ? valor : padrao(variavel, padrao);
    }

    @Override
    public int ano() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_PAINEL_ANO, ano);
    }

    @Override
    public int atualizacaoMinutos() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_PAINEL_ATUALIZACAO, atualizacaoMinutos);
    }

    @Override
    public int limiteRequerimentos() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_PAINEL_LIMITE, limiteRequerimentos);
    }

    @Override
    public int timeoutSegundos() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_PAINEL_TIMEOUT, timeoutSegundos);
    }

    @Override
    public int intervaloMinimoManualSegundos() {
        return vigentes.inteiro(CatalogoDeParametros.RASTRO_PAINEL_INTERVALO_MANUAL, intervaloMinimoManualSegundos);
    }
}
