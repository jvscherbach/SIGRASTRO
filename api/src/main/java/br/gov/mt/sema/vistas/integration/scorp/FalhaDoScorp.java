package br.gov.mt.sema.vistas.integration.scorp;

/** Falha de uma chamada ao SCORP. A mensagem é curta e técnica, sem URL, token, cookie nem identificador da pessoa. */
class FalhaDoScorp extends RuntimeException {

    private final boolean temporaria;

    FalhaDoScorp(String motivo) {
        this(motivo, false);
    }

    FalhaDoScorp(String motivo, boolean temporaria) {
        super(motivo, null, false, false);
        this.temporaria = temporaria;
    }

    /** Vale uma nova tentativa (tempo limite, queda de conexão, 5xx). */
    boolean temporaria() {
        return temporaria;
    }
}
