package br.gov.mt.sema.vistas.dossie.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Remoção best-effort de temporários: uma falha ao apagar é registrada, nunca interrompe quem chama. */
final class ArquivosTemporarios {

    private static final Logger log = LoggerFactory.getLogger(ArquivosTemporarios.class);

    private ArquivosTemporarios() {
    }

    /** Diretório próprio do pedido dentro de {@code VISTAS_DOSSIE_DIRETORIO}: documentos obtidos e o PDF final. */
    static Path pastaDo(ParametrosDoDossie parametros, PedidoDeDossie pedido) {
        return parametros.diretorio().resolve(pedido.id());
    }

    static void apagarArquivo(Path arquivo) {
        if (arquivo == null) {
            return;
        }
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            log.warn("Não foi possível apagar um arquivo temporário do dossiê: {}", e.getMessage());
        }
    }

    /** Apaga a pasta e tudo o que há dentro. */
    static void apagarPasta(Path pasta) {
        if (pasta == null || !Files.exists(pasta)) {
            return;
        }
        try (Stream<Path> caminhos = Files.walk(pasta)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(ArquivosTemporarios::apagarArquivo);
        } catch (IOException e) {
            log.warn("Não foi possível limpar a pasta temporária de um dossiê: {}", e.getMessage());
        }
    }
}
