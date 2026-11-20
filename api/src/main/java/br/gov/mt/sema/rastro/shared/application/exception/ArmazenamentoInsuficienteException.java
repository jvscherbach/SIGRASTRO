package br.gov.mt.sema.rastro.shared.application.exception;

import java.nio.file.FileSystemException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

/**
 * Sem espaço em disco para gravar um temporário (S6, bloco 3): sai como 507 {@code ARMAZENAMENTO_INSUFICIENTE}. A
 * mensagem nunca traz o caminho do arquivo nem a mensagem crua do sistema operacional (que costuma trazê-lo).
 *
 * <p>{@link #causadaPorFaltaDeEspaco} reconhece a falta de espaço numa cadeia de exceções: a mensagem do Linux
 * ({@code ENOSPC}, "No space left on device", e cota, "Disk quota exceeded") e a do Windows (em inglês e em português),
 * em qualquer {@link java.io.IOException}, {@link FileSystemException} ou {@link java.io.UncheckedIOException}
 * encadeada, inclusive nas suprimidas.
 */
public class ArmazenamentoInsuficienteException extends RastroException {

    /** Texto da geração do dossiê (gaveta, "Dossiês gerados" e o POST que recusa por falta de espaço). */
    public static final String MENSAGEM_DO_DOSSIE = "Sem espaço em disco para gerar o dossiê. Libere espaço e tente novamente.";

    /** Texto genérico (ex.: download das geometrias, que usa o mesmo cliente da API de arquivos). */
    public static final String MENSAGEM = "Sem espaço em disco no servidor. Libere espaço e tente novamente.";

    private static final List<String> SINAIS = List.of(
            "no space left on device",
            "disk quota exceeded",
            "not enough space on the disk",
            "there is not enough space",
            "espaço suficiente no disco",
            "espaco suficiente no disco",
            "não há espaço suficiente",
            "enospc",
            "edquot");

    public ArmazenamentoInsuficienteException() {
        this(MENSAGEM);
    }

    public ArmazenamentoInsuficienteException(String mensagemSegura) {
        super(ErrorCode.ARMAZENAMENTO_INSUFICIENTE, mensagemSegura);
    }

    /** Alguma exceção da cadeia (causas e suprimidas) diz que faltou espaço em disco. */
    public static boolean causadaPorFaltaDeEspaco(Throwable erro) {
        Set<Throwable> vistas = Collections.newSetFromMap(new IdentityHashMap<>());
        return procurar(erro, vistas);
    }

    private static boolean procurar(Throwable erro, Set<Throwable> vistas) {
        if (erro == null || !vistas.add(erro)) {
            return false;
        }
        if (erro instanceof ArmazenamentoInsuficienteException) {
            return true;
        }
        if (erro instanceof FileSystemException sistema && contemSinal(sistema.getReason())) {
            return true;
        }
        if (contemSinal(erro.getMessage())) {
            return true;
        }
        for (Throwable suprimida : erro.getSuppressed()) {
            if (procurar(suprimida, vistas)) {
                return true;
            }
        }
        return procurar(erro.getCause(), vistas);
    }

    private static boolean contemSinal(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String minusculo = texto.toLowerCase(Locale.ROOT);
        return SINAIS.stream().anyMatch(minusculo::contains);
    }
}
