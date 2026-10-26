package br.gov.mt.sema.vistas.dossie.infrastructure.disco;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import br.gov.mt.sema.vistas.dossie.application.port.EspacoEmDisco;
import org.springframework.stereotype.Component;

/** Espaço utilizável do volume do diretório (no Compose, o volume {@code dossies-tmp}). */
@Component
class EspacoEmDiscoDoSistema implements EspacoEmDisco {

    @Override
    public long livreEmBytes(Path diretorio) {
        try {
            return Files.getFileStore(diretorio).getUsableSpace();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
