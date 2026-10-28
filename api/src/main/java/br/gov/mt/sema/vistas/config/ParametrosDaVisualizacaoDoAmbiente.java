package br.gov.mt.sema.vistas.config;

import java.nio.file.Path;

import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaVisualizacao;
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

    ParametrosDaVisualizacaoDoAmbiente(
            @Value("${vistas.visualizacao.ttl-minutos:10}") int ttlMinutos,
            @Value("${vistas.visualizacao.cache-max-mb:500}") long cacheMaxMb,
            @Value("${vistas.visualizacao.conversoes-simultaneas:2}") int conversoesSimultaneas,
            @Value("${vistas.visualizacao.fila-de-conversoes:4}") int filaDeConversoes,
            @Value("${vistas.dossie.diretorio:/var/tmp/vistas}") String diretorioDoDossie) {
        this.ttlMinutos = ttlMinutos;
        this.cacheMaxMb = cacheMaxMb;
        this.conversoesSimultaneas = conversoesSimultaneas;
        this.filaDeConversoes = filaDeConversoes;
        this.diretorio = Path.of(diretorioDoDossie).resolve(SUBDIRETORIO);
    }

    @Override
    public int ttlMinutos() {
        return ttlMinutos;
    }

    @Override
    public long cacheMaxMb() {
        return cacheMaxMb;
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
