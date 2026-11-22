package br.gov.mt.sema.rastro.financeiro.domain;

/**
 * Resumo financeiro do requerimento (S9, bloco 3), derivado dos lançamentos avaliados <b>um a um</b> (nada é somado). Os
 * valores estão em ordem crescente de precedência: quando lançamentos diferentes apontam resumos diferentes, vale o de
 * maior precedência, {@code VENCIDO > EM_ABERTO > QUITADO > ISENTO > SEM_TAXA} ({@link #INDETERMINADO} só aparece sozinho).
 */
public enum ResumoFinanceiro {
    /** Nenhum lançamento mantido, ou só lançamentos que não pesam (cancelados, abertos já encerrados). */
    SEM_TAXA,
    /** Só isenção. */
    ISENTO,
    /** Pago (e nada em aberto). */
    QUITADO,
    /** Taxa a emitir ou aguardando pagamento no prazo. */
    EM_ABERTO,
    /** Taxa vencida (por data ou já marcada {@code VENCIDA} pelo SIGA). */
    VENCIDO,
    /**
     * Há lançamentos, mas todos com {@code STATUS} desconhecido: o Rastro não conclui nada (dizer "sem taxa" seria afirmar o
     * que não sabe). Acompanha o aviso {@code LANCAMENTO_INCONSISTENTE}.
     */
    INDETERMINADO;

    /** O de maior precedência entre os dois ({@link #INDETERMINADO} não entra na comparação). */
    ResumoFinanceiro mais(ResumoFinanceiro outro) {
        return outro.ordinal() > ordinal() ? outro : this;
    }
}
