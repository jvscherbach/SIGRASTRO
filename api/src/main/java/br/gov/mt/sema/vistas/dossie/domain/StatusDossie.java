package br.gov.mt.sema.vistas.dossie.domain;

public enum StatusDossie {

    PENDENTE,
    PROCESSANDO,
    CONCLUIDO,
    FALHA,
    CANCELADO,
    EXPIRADO;

    /** Estado em que o pedido não muda mais por conta da geração (o front para o polling). */
    public boolean encerrado() {
        return this != PENDENTE && this != PROCESSANDO;
    }
}
