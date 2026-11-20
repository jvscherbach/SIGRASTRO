package br.gov.mt.sema.rastro.dossie.infrastructure.disco;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import br.gov.mt.sema.rastro.dossie.application.port.EspacoEmDisco;
import org.springframework.stereotype.Component;

/**
 * Espaço utilizável do volume do diretório (no Compose, o volume {@code dossies-tmp}). Se o diretório ainda não existe
 * (antes do primeiro pedido), mede o volume do ancestral mais próximo que existe: é nele que a pasta será criada.
 */
@Component
class EspacoEmDiscoDoSistema implements EspacoEmDisco {

    @Override
    public long livreEmBytes(Path diretorio) {
        Path existente = diretorio.toAbsolutePath();
        while (existente != null && !Files.exists(existente)) {
            existente = existente.getParent();
        }
        if (existente == null) {
            throw new UncheckedIOException(new IOException("diretório dos dossiês sem volume conhecido"));
        }
        try {
            return Files.getFileStore(existente).getUsableSpace();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
