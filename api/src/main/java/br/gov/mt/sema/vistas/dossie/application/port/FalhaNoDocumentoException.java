package br.gov.mt.sema.vistas.dossie.application.port;

/**
 * Falha de um documento específico (não encontrado, indisponível, tempo limite, protegido por senha, corrompido...).
 * Interrompe a geração inteira (RN09). A mensagem é o motivo exibido ao usuário e nunca traz URL, token nem chave.
 * {@code tentativas} conta os pedidos feitos ao repositório para esse documento (0 quando nenhum foi feito).
 */
public class FalhaNoDocumentoException extends RuntimeException {

    private final int tentativas;

    public FalhaNoDocumentoException(String motivo) {
        this(motivo, 1);
    }

    public FalhaNoDocumentoException(String motivo, int tentativas) {
        super(motivo);
        this.tentativas = tentativas;
    }

    public FalhaNoDocumentoException(String motivo, Throwable causa) {
        super(motivo, causa);
        this.tentativas = 1;
    }

    public int tentativas() {
        return tentativas;
    }
}
