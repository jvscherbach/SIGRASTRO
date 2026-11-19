package br.gov.mt.sema.rastro.integration.licenciamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tokens de teste fictícios; nenhum token real entra aqui. */
class FonteDoTokenTest {

    @TempDir
    Path pasta;

    private Path gravar(String conteudo, Instant modificadoEm) throws IOException {
        Path arquivo = pasta.resolve("token-arquivos");
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(arquivo, FileTime.from(modificadoEm));
        return arquivo;
    }

    @Test
    void semArquivoUsaOTokenDaVariavelAparado() {
        assertThat(FonteDoToken.de("  TOKEN-FIXO-DE-TESTE \n", "").obter()).isEqualTo("TOKEN-FIXO-DE-TESTE");
        assertThat(FonteDoToken.de(null, null).obter()).isEmpty();
    }

    @Test
    void arquivoTemPrecedenciaSobreAVariavel() throws IOException {
        Path arquivo = gravar("TOKEN-DO-ARQUIVO-DE-TESTE", Instant.parse("2026-10-26T10:00:00Z"));

        assertThat(FonteDoToken.de("TOKEN-DA-VARIAVEL-DE-TESTE", arquivo.toString()).obter())
                .isEqualTo("TOKEN-DO-ARQUIVO-DE-TESTE");
    }

    @Test
    void quebraDeLinhaFinalEhAparada() throws IOException {
        Path arquivo = gravar("TOKEN-COM-QUEBRA-DE-TESTE\r\n", Instant.parse("2026-10-26T10:00:00Z"));

        assertThat(FonteDoToken.de("", arquivo.toString()).obter()).isEqualTo("TOKEN-COM-QUEBRA-DE-TESTE");
    }

    @Test
    void trocaDoConteudoEntreDuasChamadasValeNaSegunda() throws IOException {
        Path arquivo = gravar("TOKEN-ANTIGO-DE-TESTE", Instant.parse("2026-10-26T10:00:00Z"));
        FonteDoToken fonte = FonteDoToken.de("", arquivo.toString());
        assertThat(fonte.obter()).isEqualTo("TOKEN-ANTIGO-DE-TESTE");

        gravar("TOKEN-NOVO-DE-TESTE", Instant.parse("2026-10-26T10:05:00Z"));

        assertThat(fonte.obter()).isEqualTo("TOKEN-NOVO-DE-TESTE");
    }

    @Test
    void trocaComMesmaDataMasOutroTamanhoTambemEhPercebida() throws IOException {
        Instant mesmoInstante = Instant.parse("2026-10-26T10:00:00Z");
        Path arquivo = gravar("TOKEN-A", mesmoInstante);
        FonteDoToken fonte = FonteDoToken.de("", arquivo.toString());
        assertThat(fonte.obter()).isEqualTo("TOKEN-A");

        gravar("TOKEN-BEM-MAIS-LONGO", mesmoInstante);

        assertThat(fonte.obter()).isEqualTo("TOKEN-BEM-MAIS-LONGO");
    }

    @Test
    void arquivoVazioOuSoComEspacosEhApiNaoConfigurada() throws IOException {
        for (String conteudo : new String[] {"", "  \n"}) {
            Path arquivo = gravar(conteudo, Instant.parse("2026-10-26T10:00:00Z"));

            assertThatThrownBy(() -> FonteDoToken.de("TOKEN-DA-VARIAVEL-DE-TESTE", arquivo.toString()).obter())
                    .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("API de arquivos não configurada");
        }
    }

    @Test
    void arquivoAusenteEhApiNaoConfiguradaSemMostrarOCaminho() {
        Path ausente = pasta.resolve("nao-existe");

        assertThatThrownBy(() -> FonteDoToken.de("", ausente.toString()).obter())
                .isInstanceOf(FalhaNoDocumentoException.class).hasMessage("API de arquivos não configurada")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(ausente.toString()));
    }

    @Test
    void arquivoQueSomeDepoisDeLidoTambemFalha() throws IOException {
        Path arquivo = gravar("TOKEN-DE-TESTE", Instant.parse("2026-10-26T10:00:00Z"));
        FonteDoToken fonte = FonteDoToken.de("", arquivo.toString());
        assertThat(fonte.obter()).isEqualTo("TOKEN-DE-TESTE");

        Files.delete(arquivo);

        assertThatThrownBy(fonte::obter).isInstanceOf(FalhaNoDocumentoException.class);
    }
}
