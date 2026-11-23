package br.gov.mt.sema.rastro.financeiro.domain;

import java.util.List;

/**
 * O que a leitura de um requerimento trouxe do SIGA, cru. {@code alemDoLimite}: havia mais lançamentos do que o limite por
 * requerimento ({@code RASTRO_FINANCEIRO_LIMITE_LANCAMENTOS}); os excedentes (os mais recentes) ficaram de fora e a
 * classificação avisa, em vez de calar.
 */
public record LeituraDosLancamentos(List<Lancamento> lancamentos, boolean alemDoLimite) {

    public static final LeituraDosLancamentos NENHUM = new LeituraDosLancamentos(List.of(), false);

    public LeituraDosLancamentos {
        lancamentos = List.copyOf(lancamentos);
    }
}
