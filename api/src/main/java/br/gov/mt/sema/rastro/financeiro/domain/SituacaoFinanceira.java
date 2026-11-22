package br.gov.mt.sema.rastro.financeiro.domain;

/**
 * Situação de um lançamento (S9, bloco 3), tirada do {@code STATUS} como o Licenciamento o grava: o Rastro <b>não troca</b>
 * a situação. A "taxa vencida por data" (o SIGA não marca a taxa como vencida quando o prazo passa) é uma marca à parte
 * ({@link LancamentoAvaliado#vencidaPorData()}), não uma troca para {@link #VENCIDA}.
 */
public enum SituacaoFinanceira {
    /** {@code AGUARDANDO_EMISSAO}. */
    EMITINDO,
    AGUARDANDO_PAGAMENTO,
    PAGA,
    VENCIDA,
    ISENTA,
    CANCELADA,
    /** {@code STATUS} fora do enum: mostrado como está, com o aviso {@code LANCAMENTO_INCONSISTENTE}; não entra no resumo. */
    DESCONHECIDA;

    static SituacaoFinanceira de(Lancamento l) {
        return l.status().map(s -> switch (s) {
            case AGUARDANDO_EMISSAO -> EMITINDO;
            case AGUARDANDO_PAGAMENTO -> AGUARDANDO_PAGAMENTO;
            case PAGA -> PAGA;
            case VENCIDA -> VENCIDA;
            case ISENTA -> ISENTA;
            case CANCELADA -> CANCELADA;
        }).orElse(DESCONHECIDA);
    }
}
