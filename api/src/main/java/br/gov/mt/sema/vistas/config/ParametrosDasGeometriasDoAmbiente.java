package br.gov.mt.sema.vistas.config;

import java.nio.file.Path;

import br.gov.mt.sema.vistas.documento.application.port.ParametrosDasGeometrias;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Parâmetros do ZIP das geometrias vindos de variáveis de ambiente (protótipo). Temporários em {@code VISTAS_DOSSIE_DIRETORIO/geometrias}. */
@Component
class ParametrosDasGeometriasDoAmbiente implements ParametrosDasGeometrias {

    static final String SUBDIRETORIO = "geometrias";

    private final long maximoMb;
    private final int maximoDeArquivos;
    private final long espacoMinimoMb;
    private final Path diretorio;

    ParametrosDasGeometriasDoAmbiente(
            @Value("${vistas.geometrias.max-mb:500}") long maximoMb,
            @Value("${vistas.geometrias.max-arquivos:200}") int maximoDeArquivos,
            @Value("${vistas.dossie.espaco-minimo-mb:2048}") long espacoMinimoMb,
            @Value("${vistas.dossie.diretorio:/var/tmp/vistas}") String diretorioDoDossie) {
        this.maximoMb = maximoMb;
        this.maximoDeArquivos = maximoDeArquivos;
        this.espacoMinimoMb = espacoMinimoMb;
        this.diretorio = Path.of(diretorioDoDossie).resolve(SUBDIRETORIO);
    }

    @Override
    public long maximoMb() {
        return maximoMb;
    }

    @Override
    public int maximoDeArquivos() {
        return maximoDeArquivos;
    }

    @Override
    public long espacoMinimoMb() {
        return espacoMinimoMb;
    }

    @Override
    public Path diretorio() {
        return diretorio;
    }
}
