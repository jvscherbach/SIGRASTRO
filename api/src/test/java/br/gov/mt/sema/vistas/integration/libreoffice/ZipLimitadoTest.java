package br.gov.mt.sema.vistas.integration.libreoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.Motivo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Limites do ZIP antes do LibreOffice. Não exige o LibreOffice: roda sempre. */
class ZipLimitadoTest {

    @TempDir
    Path pasta;

    @Test
    void documentoNormalPassa() throws Exception {
        Path docx = Files.write(pasta.resolve("a.docx"), DocumentosDeTeste.docx("texto"));
        assertThatCode(() -> ZipLimitado.conferir(docx)).doesNotThrowAnyException();
    }

    @Test
    void naoZipPassaDireto() throws Exception {
        Path rtf = Files.write(pasta.resolve("a.rtf"), DocumentosDeTeste.rtf("texto"));
        Path vazio = Files.write(pasta.resolve("v.docx"), new byte[0]);
        assertThatCode(() -> ZipLimitado.conferir(rtf)).doesNotThrowAnyException();
        assertThatCode(() -> ZipLimitado.conferir(vazio)).doesNotThrowAnyException();
    }

    @Test
    void descompactadoAcimaDoLimiteParaNaHora() throws Exception {
        Path bomba = ArquivosHostis.zipBombaComoDocx(pasta.resolve("b.docx"), 64L * 1024 * 1024);
        assertThat(Files.size(bomba)).isLessThan(1024 * 1024);
        assertThatThrownBy(() -> ZipLimitado.conferir(bomba, 16L * 1024 * 1024, 100))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        f -> assertThat(f.motivo()).isEqualTo(Motivo.ENTRADA_GRANDE_DEMAIS));
    }

    @Test
    void entradasDemaisFalham() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < 11; i++) {
                zip.putNextEntry(new ZipEntry("e" + i));
                zip.closeEntry();
            }
        }
        Path muitas = Files.write(pasta.resolve("m.xlsx"), bytes.toByteArray());
        assertThatThrownBy(() -> ZipLimitado.conferir(muitas, 1024, 10))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        f -> assertThat(f.motivo()).isEqualTo(Motivo.ENTRADA_GRANDE_DEMAIS));
    }

    @Test
    void zipIlegivelEhCorrompido() throws Exception {
        Path lixo = Files.write(pasta.resolve("l.docx"), DocumentosDeTeste.zipCorrompido());
        assertThatThrownBy(() -> ZipLimitado.conferir(lixo))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        f -> assertThat(f.motivo()).isEqualTo(Motivo.CORROMPIDO));
    }
}
