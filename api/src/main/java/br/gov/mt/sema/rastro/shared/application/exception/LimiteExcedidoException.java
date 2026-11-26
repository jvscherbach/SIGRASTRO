package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

/** Limite de tentativas esgotado: 429 com o tempo restante, sem dizer se o login existe. */
public class LimiteExcedidoException extends RastroException {

    private final long segundosRestantes;

    public LimiteExcedidoException(long segundosRestantes) {
        super(ErrorCode.MUITAS_TENTATIVAS, mensagem(segundosRestantes));
        this.segundosRestantes = Math.max(1, segundosRestantes);
    }

    public long segundosRestantes() {
        return segundosRestantes;
    }

    private static String mensagem(long segundos) {
        long minutos = Math.max(1, (segundos + 59) / 60);
        return "Muitas tentativas. Tente de novo em " + minutos + (minutos == 1 ? " minuto." : " minutos.");
    }
}
