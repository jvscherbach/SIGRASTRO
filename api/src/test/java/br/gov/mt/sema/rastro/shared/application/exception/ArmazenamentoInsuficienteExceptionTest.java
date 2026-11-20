package br.gov.mt.sema.rastro.shared.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Reconhecimento de "sem espaço em disco" numa cadeia de exceções (S6, bloco 3). Nenhum disco é enchido. */
class ArmazenamentoInsuficienteExceptionTest {

    @Test
    void reconheceAMensagemDoLinuxEmFileSystemExceptionPeloMotivo() {
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(
                new FileSystemException("/tmp/x", null, "No space left on device"))).isTrue();
    }

    @Test
    void reconheceCotaDoLinuxEMensagensDoWindowsEmInglesEPortugues() {
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(new IOException("Disk quota exceeded")))
                .isTrue();
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(
                new IOException("There is not enough space on the disk"))).isTrue();
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(
                new IOException("Não há espaço suficiente no disco"))).isTrue();
    }

    @Test
    void reconheceEncadeadaEmUncheckedIOExceptionEmOutraExcecaoENasSuprimidas() {
        IOException semEspaco = new IOException("No space left on device");
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(
                new IllegalStateException("Falha ao montar", new UncheckedIOException(semEspaco)))).isTrue();
        RuntimeException comSuprimida = new RuntimeException("falhou ao fechar");
        comSuprimida.addSuppressed(semEspaco);
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(comSuprimida)).isTrue();
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(
                new RuntimeException(new ArmazenamentoInsuficienteException()))).isTrue();
    }

    @Test
    void naoConfundeOutrasFalhasDeEntradaESaida() {
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(new IOException("Permission denied")))
                .isFalse();
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(new IOException((String) null)))
                .isFalse();
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(null)).isFalse();
    }

    @Test
    void cadeiaCircularNaoTravaNemEstoura() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.addSuppressed(b);
        assertThat(ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(b)).isFalse();
    }

    @Test
    void sai507ComMensagemSemCaminho() {
        ArmazenamentoInsuficienteException e = new ArmazenamentoInsuficienteException(
                ArmazenamentoInsuficienteException.MENSAGEM_DO_DOSSIE);
        assertThat(e.errorCode()).isEqualTo(ErrorCode.ARMAZENAMENTO_INSUFICIENTE);
        assertThat(e.errorCode().status()).isEqualTo(HttpStatus.INSUFFICIENT_STORAGE);
        assertThat(e.getMessage()).isEqualTo("Sem espaço em disco para gerar o dossiê. Libere espaço e tente novamente.")
                .doesNotContain("/");
    }
}
