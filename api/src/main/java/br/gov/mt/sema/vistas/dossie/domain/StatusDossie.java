package br.gov.mt.sema.vistas.dossie.domain;

/** Situações do pedido (S2.2, docs/06). Não há "falha" terminal: falhas deixam o pedido em PENDENTE_DECISAO. */
public enum StatusDossie {

    AGUARDANDO,
    OBTENDO,
    PENDENTE_DECISAO,
    MONTANDO,
    CONCLUIDO,
    CANCELADO,
    EXPIRADO;

    /** A geração está rodando ou na fila: o executor é dono do pedido. */
    public boolean emAndamento() {
        return this == AGUARDANDO || this == OBTENDO || this == MONTANDO;
    }

    /** Não muda mais: só resta esquecer o registro. */
    public boolean terminal() {
        return this == CANCELADO || this == EXPIRADO;
    }
}
