package br.gov.mt.sema.rastro.integration.licenciamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Teste de fumaça contra a API de arquivos REAL, somente leitura (um GET), para o responsável executar na
 * homologação (docs/homologacao/S2-roteiro-homologacao.md). Nunca roda no verify normal: só com ARQUIVOS_SMOKE=true.
 * Baixa UM arquivo para a pasta temporária do teste, confere se é PDF e não imprime token, URL nem chave.
 *
 * <pre>
 * ARQUIVOS_SMOKE=true LICENCIAMENTO_API_URL=... LICENCIAMENTO_API_TOKEN=... ARQUIVOS_SMOKE_CHAVE=... \
 *   ./mvnw -Dtest=ArquivosFumacaTest test
 * </pre>
 *
 * Modo cookie: acrescente LICENCIAMENTO_API_AUTH_MODE=cookie e LICENCIAMENTO_API_COOKIE_NAME.
 */
@EnabledIfEnvironmentVariable(named = "ARQUIVOS_SMOKE", matches = "true")
class ArquivosFumacaTest {

    @TempDir
    Path pasta;

    @Test
    void baixaUmArquivoRealEConfereAAssinaturaDePdf() throws IOException {
        ArquivosDoLicenciamentoHttp adapter = new ArquivosDoLicenciamentoHttp(System.getenv("LICENCIAMENTO_API_URL"),
                System.getenv("LICENCIAMENTO_API_TOKEN"), System.getenv("LICENCIAMENTO_API_AUTH_MODE"),
                System.getenv("LICENCIAMENTO_API_COOKIE_NAME"), () -> new LimitesDeDownload(LimitesDeDownload.CONEXAO_PADRAO,
                Duration.ofSeconds(120), Duration.ofSeconds(900)), d -> Thread.sleep(d.toMillis()));
        Path destino = pasta.resolve("fumaca.pdf");

        adapter.baixar(System.getenv("ARQUIVOS_SMOKE_CHAVE"), destino);

        assertThat(destino).exists();
        try (InputStream entrada = Files.newInputStream(destino)) {
            String inicio = new String(entrada.readNBytes(1024), StandardCharsets.ISO_8859_1);
            assertThat(inicio).contains("%PDF-");
        }
    }
}
