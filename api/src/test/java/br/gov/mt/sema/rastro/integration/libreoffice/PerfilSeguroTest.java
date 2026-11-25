package br.gov.mt.sema.rastro.integration.libreoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Os dois perfis-modelo (V6, bloco 3): só o estrito liga DisableActiveContent; o resto das proteções é igual. */
class PerfilSeguroTest {

    @TempDir
    Path pasta;

    @Test
    void perfilEstritoMantemDisableActiveContent() throws IOException {
        String xcu = xcu(PerfilSeguro.criar(pasta.resolve("estrito")));

        assertThat(xcu).contains("DisableActiveContent");
        assertThat(xcu).contains("DisableMacrosExecution");
    }

    @Test
    void perfilDeGraficosNaoTemDisableActiveContentMasMantemAsDemaisProtecoes() throws IOException {
        String estrito = xcu(PerfilSeguro.criar(pasta.resolve("e")));
        String graficos = xcu(PerfilSeguro.criar(pasta.resolve("g"), false));

        assertThat(graficos).doesNotContain("DisableActiveContent");
        assertThat(graficos).contains("MacroSecurityLevel", "DisableMacrosExecution", "BlockUntrustedRefererLinks",
                "ooInetProxyType", "/org.openoffice.Office.Calc/Content/Update");
        assertThat(graficos).isEqualTo(estrito.replaceAll("(?m)^.*DisableActiveContent.*\\R", ""));
    }

    private static String xcu(Path modelo) throws IOException {
        return Files.readString(modelo.resolve("user").resolve("registrymodifications.xcu"), StandardCharsets.UTF_8);
    }
}
