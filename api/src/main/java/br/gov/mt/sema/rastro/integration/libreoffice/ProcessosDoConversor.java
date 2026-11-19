package br.gov.mt.sema.rastro.integration.libreoffice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Localiza os processos do LibreOffice pelo diretório de trabalho do conversor, que aparece na linha de comando
 * ({@code -env:UserInstallation=...}). Garante que nenhum {@code soffice} fique órfão: os do próprio conversor, ao
 * parar, e os de uma execução anterior que caiu sem parar o conversor, na subida.
 *
 * <p>Cada diretório de trabalho tem um arquivo {@value #DONO} com o processo da aplicação que o criou (pid e
 * instante de início, contra reuso de pid). Diretório cujo dono não está mais vivo é de uma execução anterior.
 */
final class ProcessosDoConversor {

    /** Prefixo do diretório de trabalho de cada instância do conversor (um por aplicação). */
    static final String PREFIXO = "rastro-conversor-";
    static final String DONO = "dono";

    private ProcessosDoConversor() {
    }

    /** Processos cuja linha de comando cita {@code marca}. */
    static List<ProcessHandle> comMarca(String marca) {
        long proprio = ProcessHandle.current().pid();
        try (Stream<ProcessHandle> todos = ProcessHandle.allProcesses()) {
            return todos.filter(p -> p.pid() != proprio)
                    .filter(p -> linhaDeComando(p).map(c -> c.contains(marca)).orElse(false))
                    .toList();
        }
    }

    /** Encerra à força os processos que citam {@code marca} e espera até {@code espera} que saiam. */
    static int encerrar(String marca, Duration espera) {
        List<ProcessHandle> processos = comMarca(marca);
        processos.forEach(ProcessHandle::destroyForcibly);
        long limite = System.nanoTime() + espera.toNanos();
        for (ProcessHandle processo : processos) {
            try {
                processo.onExit().get(Math.max(0, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException | ExecutionException e) {
                // Segue: os demais ainda podem ter saído.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return processos.size();
    }

    /** Registra esta aplicação como dona do diretório de trabalho. */
    static void registrarDono(Path trabalho) throws IOException {
        ProcessHandle atual = ProcessHandle.current();
        Files.writeString(trabalho.resolve(DONO), identidade(atual).orElse(String.valueOf(atual.pid())),
                StandardCharsets.UTF_8);
    }

    /**
     * Encerra os processos dos diretórios de trabalho em {@code base} cujo dono não está mais vivo e apaga esses
     * diretórios. Os de outras instâncias vivas (outra aplicação, outro teste) ficam.
     */
    static int encerrarOrfaosAnteriores(Path base, Duration espera) {
        int encerrados = 0;
        List<Path> anteriores;
        try (Stream<Path> filhos = Files.list(base)) {
            anteriores = filhos.filter(p -> p.getFileName().toString().startsWith(PREFIXO)).toList();
        } catch (IOException e) {
            return 0;
        }
        for (Path anterior : anteriores) {
            if (!donoVivo(anterior)) {
                encerrados += encerrar(anterior.getFileName().toString(), espera);
                ConversorLibreOffice.apagarPasta(anterior);
            }
        }
        return encerrados;
    }

    private static boolean donoVivo(Path trabalho) {
        String registrado;
        try {
            registrado = Files.readString(trabalho.resolve(DONO), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return false;
        }
        long pid;
        try {
            pid = Long.parseLong(registrado.split(" ", 2)[0]);
        } catch (NumberFormatException e) {
            return false;
        }
        return ProcessHandle.of(pid).filter(ProcessHandle::isAlive)
                .map(p -> identidade(p).map(registrado::equals).orElse(true))
                .orElse(false);
    }

    private static Optional<String> identidade(ProcessHandle processo) {
        return processo.info().startInstant().map(inicio -> processo.pid() + " " + inicio);
    }

    private static Optional<String> linhaDeComando(ProcessHandle processo) {
        ProcessHandle.Info info = processo.info();
        return info.commandLine().or(() -> info.arguments().map(a -> String.join(" ", a)));
    }
}
