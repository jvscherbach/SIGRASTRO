package br.gov.mt.sema.rastro.integration.libreoffice;

import static br.gov.mt.sema.rastro.integration.libreoffice.ConversorLibreOfficeTest.OFFICE_HOME;
import static br.gov.mt.sema.rastro.integration.libreoffice.ConversorLibreOfficeTest.texto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import br.gov.mt.sema.rastro.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.rastro.documento.domain.TipoReal;
import br.gov.mt.sema.rastro.integration.libreoffice.ConversorLibreOfficeTest.Parametros;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Processos do LibreOffice com problema: tarefa que não termina, processo morto, reinício a cada N tarefas. Cada teste
 * tem o próprio pool, porque derruba processos, e termina sem nenhum {@code soffice} órfão. Exige o LibreOffice.
 */
class ProcessosDoConversorTest {

    @TempDir
    Path pasta;

    /** Por teste, para que cada um apareça como pulado no relatório, com a mensagem. */
    @BeforeEach
    void exigirSoffice() {
        ConversorLibreOfficeTest.exigirSoffice();
    }

    @Test
    void tarefaQueNaoTerminaFalhaPorTempoLimiteEAProximaFunciona() throws Exception {
        assumeTrue(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"),
                "simulação de travamento por SIGSTOP só em Linux/macOS");
        ConversorLibreOffice alvo = ConversorLibreOfficeTest.conversor(new Parametros(true, 5, 1, 50), OFFICE_HOME, pasta);
        try {
            converter(alvo, DocumentosDeTeste.docx("aquecimento"));
            // Congela o soffice: a próxima tarefa nunca termina, como um documento que trava o conversor.
            for (ProcessHandle processo : ProcessosDoConversor.comMarca(alvo.marca())) {
                new ProcessBuilder("kill", "-STOP", String.valueOf(processo.pid())).start().waitFor();
            }
            long inicio = System.nanoTime();
            ConversorLibreOfficeTest.assertFalha(pasta, alvo, "docx", DocumentosDeTeste.docx("travado"),
                    Motivo.TEMPO_LIMITE);
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(60));

            assertThat(texto(converter(alvo, DocumentosDeTeste.docx(DocumentosDeTeste.TEXTO))))
                    .contains(DocumentosDeTeste.TEXTO);
        } finally {
            encerrarSemOrfaos(alvo);
        }
    }

    @Test
    void interromperAThreadEncerraAEsperaComMotivoProprioSemEsperarOTimeout() throws Exception {
        assumeTrue(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"),
                "simulação de travamento por SIGSTOP só em Linux/macOS");
        ConversorLibreOffice alvo = ConversorLibreOfficeTest.conversor(new Parametros(true, 120, 1, 50), OFFICE_HOME, pasta);
        try {
            converter(alvo, DocumentosDeTeste.docx("aquecimento"));
            for (ProcessHandle processo : ProcessosDoConversor.comMarca(alvo.marca())) {
                new ProcessBuilder("kill", "-STOP", String.valueOf(processo.pid())).start().waitFor();
            }
            java.util.concurrent.atomic.AtomicReference<Throwable> falha = new java.util.concurrent.atomic.AtomicReference<>();
            Thread conversao = new Thread(() -> {
                try {
                    converter(alvo, DocumentosDeTeste.docx("travado"));
                } catch (Throwable e) {
                    falha.set(e);
                }
            });
            conversao.start();
            Thread.sleep(2000); // a tarefa já está no soffice congelado
            long inicio = System.nanoTime();
            conversao.interrupt();
            conversao.join(30_000);

            assertThat(conversao.isAlive()).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(30));
            assertThat(falha.get()).isInstanceOfSatisfying(
                    br.gov.mt.sema.rastro.documento.application.port.FalhaDeConversaoException.class,
                    f -> assertThat(f.motivo()).isEqualTo(Motivo.INTERROMPIDA));
        } finally {
            // Descongela: parar um soffice congelado faria o JODConverter esperar o próprio prazo antes de forçar.
            for (ProcessHandle processo : ProcessosDoConversor.comMarca(alvo.marca())) {
                new ProcessBuilder("kill", "-CONT", String.valueOf(processo.pid())).start().waitFor();
            }
            encerrarSemOrfaos(alvo);
        }
    }

    @Test
    void processoMortoNoMeioNaoImpedeAProximaTarefa() throws Exception {
        ConversorLibreOffice alvo = ConversorLibreOfficeTest.conversor(new Parametros(true, 30, 1, 50), OFFICE_HOME, pasta);
        try {
            converter(alvo, DocumentosDeTeste.docx("aquecimento"));
            List<ProcessHandle> processos = ProcessosDoConversor.comMarca(alvo.marca());
            assertThat(processos).isNotEmpty();
            processos.forEach(ProcessHandle::destroyForcibly);

            assertThat(texto(converter(alvo, DocumentosDeTeste.docx(DocumentosDeTeste.TEXTO))))
                    .contains(DocumentosDeTeste.TEXTO);
        } finally {
            encerrarSemOrfaos(alvo);
        }
    }

    @Test
    void reiniciaOProcessoACadaNTarefasSemPerderConversoes() throws Exception {
        ConversorLibreOffice alvo = ConversorLibreOfficeTest.conversor(new Parametros(true, 30, 2, 2), OFFICE_HOME, pasta);
        try {
            for (int i = 0; i < 5; i++) {
                Path pdf = ConversorLibreOfficeTest.converter(pasta, alvo, "rtf",
                        DocumentosDeTeste.rtf("documento " + i), TipoReal.TEXTO);
                assertThat(texto(pdf)).contains("documento " + i);
            }
        } finally {
            encerrarSemOrfaos(alvo);
        }
    }

    @Test
    void processosDeUmaExecucaoAnteriorQueCaiuSaoEncerradosNaSubida() throws Exception {
        assumeTrue(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"),
                "órfão simulado com sh só em Linux/macOS");
        // Diretório de uma execução anterior cujo dono (um processo que já terminou) morreu, e um "soffice" que ficou
        // com a marca dele na linha de comando.
        Process donoMorto = new ProcessBuilder("true").start();
        donoMorto.waitFor();
        String marcaAntiga = ProcessosDoConversor.PREFIXO + "anterior";
        Path antigo = Files.createDirectories(pasta.resolve(marcaAntiga));
        Files.writeString(antigo.resolve(ProcessosDoConversor.DONO), String.valueOf(donoMorto.pid()));
        Process orfao = new ProcessBuilder("sh", "-c", "while :; do sleep 1; done",
                "-env:UserInstallation=file://" + antigo).start();
        assertThat(ProcessosDoConversor.comMarca(marcaAntiga)).isNotEmpty();

        ConversorLibreOffice alvo = ConversorLibreOfficeTest.conversor(new Parametros(true, 30, 1, 50), OFFICE_HOME, pasta);
        try {
            converter(alvo, DocumentosDeTeste.rtf("subida"));
            assertThat(orfao.isAlive()).as("órfão da execução anterior").isFalse();
            assertThat(antigo).doesNotExist();
        } finally {
            orfao.destroyForcibly();
            encerrarSemOrfaos(alvo);
        }
    }

    private Path converter(ConversorLibreOffice alvo, byte[] docx) throws Exception {
        boolean rtf = docx.length > 0 && docx[0] == '{';
        return ConversorLibreOfficeTest.converter(pasta, alvo, rtf ? "rtf" : "docx", docx, TipoReal.TEXTO);
    }

    private static void encerrarSemOrfaos(ConversorLibreOffice alvo) {
        String marca = alvo.marca();
        alvo.destroy();
        assertThat(ProcessosDoConversor.comMarca(marca)).as("nenhum soffice órfão").isEmpty();
    }
}
