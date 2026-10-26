package br.gov.mt.sema.vistas.dossie.application.port;

/**
 * Falha de um documento específico (não encontrado, indisponível, tempo limite, protegido por senha, corrompido...).
 * Interrompe a geração inteira (RN09). A mensagem é o motivo exibido ao usuário e nunca traz URL, token nem chave.
 */
public class FalhaNoDocumentoException extends RuntimeException {

    public FalhaNoDocumentoException(String motivo) {
        super(motivo);
    }

    public FalhaNoDocumentoException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
