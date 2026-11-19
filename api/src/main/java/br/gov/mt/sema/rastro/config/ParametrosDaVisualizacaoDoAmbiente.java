package br.gov.mt.sema.rastro.config;

import java.nio.file.Path;

import br.gov.mt.sema.rastro.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import br.gov.mt.sema.rastro.documento.application.port.ParametrosDaVisualizacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Parâmetros do modal vindos de variáveis de ambiente (protótipo). O cache fica em {@code VISTAS_DOSSIE_DIRETORIO/visualizacao}. */
@Component
class ParametrosDaVisualizacaoDoAmbiente implements ParametrosDaVisualizacao {

    static final String SUBDIRETORIO = "visualizacao";

    private final int ttlMinutos;
    private final long cacheMaxMb;
    private final int conversoesSimultaneas;
    private final int filaDeConversoes;
    private final Path diretorio;
    private final ParametrosEmVigor vigentes;

    ParametrosDaVisualizacaoDoAmbiente(
            @Value("${rastro.visualizacao.ttl-minutos:10}") int ttlMinutos,
            @Value("${rastro.visualizacao.cache-max-mb:500}") long cacheMaxMb,
            @Value("${rastro.visualizacao.conversoes-simultaneas:2}") int conversoesSimultaneas,
            @Value("${rastro.visualizacao.fila-de-conversoes:4}") int filaDeConversoes,
            @Value("${rastro.dossie.diretorio:/var/tmp/vistas}") String diretorioDoDossie, ParametrosEmVigor vigentes) {
        this.vigentes = vigentes;
        this.ttlMinutos = ttlMinutos;
        this.cacheMaxMb = cacheMaxMb;
        this.conversoesSimultaneas = conversoesSimultaneas;
        this.filaDeConversoes = filaDeConversoes;
        this.diretorio = Path.of(diretorioDoDossie).resolve(SUBDIRETORIO);
    }

    @Override
    public int ttlMinutos() {
        return vigentes.inteiro(CatalogoDeParametros.VISUALIZACAO_TTL, ttlMinutos);
    }

    @Override
    public long cacheMaxMb() {
        return vigentes.inteiro(CatalogoDeParametros.VISUALIZACAO_CACHE_MB, (int) cacheMaxMb);
    }

    @Override
    public int conversoesSimultaneas() {
        return conversoesSimultaneas;
    }

    @Override
    public int filaDeConversoes() {
        return filaDeConversoes;
    }

    @Override
    public Path diretorio() {
        return diretorio;
    }
}
