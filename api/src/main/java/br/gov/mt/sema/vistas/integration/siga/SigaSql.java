package br.gov.mt.sema.vistas.integration.siga;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;

/** Carrega as consultas de src/main/resources/sql/siga. */
final class SigaSql {

    private SigaSql() {
    }

    static String carregar(String nome) {
        ClassPathResource recurso = new ClassPathResource("sql/siga/" + nome + ".sql");
        try (InputStream entrada = recurso.getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Consulta " + nome + " não encontrada", e);
        }
    }
}
