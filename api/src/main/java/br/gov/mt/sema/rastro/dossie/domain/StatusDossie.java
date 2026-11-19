package br.gov.mt.sema.rastro.dossie.domain;

/**
 * Situações do pedido (S2.2, docs/06). Falhas de documento deixam o pedido em PENDENTE_DECISAO (RN09). FALHA (S6,
 * bloco 3) é só para o que nenhuma decisão resolve: sem espaço em disco ou o arquivo final perdido. O diretório do
 * pedido já foi apagado e o único caminho é gerar de novo.
 */
public enum StatusDossie {

    AGUARDANDO,
    OBTENDO,
    PENDENTE_DECISAO,
    MONTANDO,
    CONCLUIDO,
    CANCELADO,
    EXPIRADO,
    FALHA;

    /** A geração está rodando ou na fila: o executor é dono do pedido. */
    public boolean emAndamento() {
        return this == AGUARDANDO || this == OBTENDO || this == MONTANDO;
    }

    /** Não muda mais: só resta esquecer o registro. */
    public boolean terminal() {
        return this == CANCELADO || this == EXPIRADO || this == FALHA;
    }
}
