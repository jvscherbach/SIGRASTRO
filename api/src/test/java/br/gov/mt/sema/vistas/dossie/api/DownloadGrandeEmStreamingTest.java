package br.gov.mt.sema.vistas.dossie.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import br.gov.mt.sema.vistas.dossie.application.CancelarDossie;
import br.gov.mt.sema.vistas.dossie.application.ConsultarDossie;
import br.gov.mt.sema.vistas.dossie.application.ContinuarDossie;
import br.gov.mt.sema.vistas.dossie.application.CriarDossie;
import br.gov.mt.sema.vistas.dossie.application.EmitirTicketDeDownload;
import br.gov.mt.sema.vistas.dossie.application.ObterArquivoDoDossie;
import br.gov.mt.sema.vistas.dossie.application.ObterArquivoDoDossie.ArquivoDoDossie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

/**
 * Dossiê grande (D49): o corpo é um fluxo do arquivo em disco, lido sob demanda, e o diretório só é apagado quando o
 * cliente recebe tudo. Chama o controller direto para não passar pelo buffer do MockMvc.
 */
class DownloadGrandeEmStreamingTest {

    private static final long TAMANHO = 200L * 1024 * 1024;

    @TempDir
    Path pasta;

    @Test
    void arquivoDe200MbSaiEmBlocosEOAvisoDeFimSoVemNoUltimoByte() throws IOException {
        Path grande = pasta.resolve("dossie.pdf");
        try (RandomAccessFile arquivo = new RandomAccessFile(grande.toFile(), "rw")) {
            arquivo.setLength(TAMANHO);
        }
        ObterArquivoDoDossie obter = mock(ObterArquivoDoDossie.class);
        ArquivoDoDossie dossie = new ArquivoDoDossie("pedido-1", grande, "dossie-processo-2825-2025.pdf", TAMANHO);
        when(obter.executarPorTicket("pedido-1", "ticket-de-teste")).thenReturn(dossie);
        DossieController controller = new DossieController(mock(CriarDossie.class), mock(ConsultarDossie.class), obter,
                mock(CancelarDossie.class), mock(ContinuarDossie.class), mock(EmitirTicketDeDownload.class),
                mock(br.gov.mt.sema.vistas.dossie.application.ListarDossiesGerados.class));

        ResponseEntity<InputStreamResource> resposta = controller.arquivo("pedido-1", "attachment", "ticket-de-teste");

        assertThat(resposta.getHeaders().getContentLength()).isEqualTo(TAMANHO);
        long lidos = 0;
        byte[] bloco = new byte[64 * 1024];
        try (InputStream corpo = resposta.getBody().getInputStream()) {
            int n;
            while ((n = corpo.read(bloco)) > 0) {
                lidos += n;
                if (lidos == bloco.length) {
                    verify(obter, never()).concluirDownload(any(ArquivoDoDossie.class));
                }
            }
        }
        assertThat(lidos).isEqualTo(TAMANHO);
        verify(obter).concluirDownload(dossie);
    }

    @Test
    void downloadInterrompidoNaoApagaODossie() throws IOException {
        Path grande = pasta.resolve("dossie.pdf");
        try (RandomAccessFile arquivo = new RandomAccessFile(grande.toFile(), "rw")) {
            arquivo.setLength(TAMANHO);
        }
        ObterArquivoDoDossie obter = mock(ObterArquivoDoDossie.class);
        when(obter.executarPorTicket("pedido-1", "ticket-de-teste"))
                .thenReturn(new ArquivoDoDossie("pedido-1", grande, "dossie.pdf", TAMANHO));
        DossieController controller = new DossieController(mock(CriarDossie.class), mock(ConsultarDossie.class), obter,
                mock(CancelarDossie.class), mock(ContinuarDossie.class), mock(EmitirTicketDeDownload.class),
                mock(br.gov.mt.sema.vistas.dossie.application.ListarDossiesGerados.class));

        try (InputStream corpo = controller.arquivo("pedido-1", "attachment", "ticket-de-teste").getBody().getInputStream()) {
            corpo.readNBytes(1024 * 1024);
        }

        verify(obter, never()).concluirDownload(any(ArquivoDoDossie.class));
    }
}
