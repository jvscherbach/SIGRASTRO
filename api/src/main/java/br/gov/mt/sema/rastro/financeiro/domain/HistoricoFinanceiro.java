package br.gov.mt.sema.rastro.financeiro.domain;

import java.util.List;

/**
 * O histórico financeiro avaliado de um requerimento (S9, blocos 3 e 4): os lançamentos mantidos em ordem, o resumo, os
 * registros "Isento de Taxa" (um por isenção, com a data da cadeia de apoio) e os avisos (os da validade, do bloco 2, mais
 * o de taxa vencida por data). {@code classificados} guarda os ignorados, só para o diagnóstico do Administrador.
 */
public record HistoricoFinanceiro(List<LancamentoAvaliado> lancamentos, ResumoFinanceiro resumo,
        List<RegistroDeIsencao> isencoes, List<AvisoFinanceiro> avisos, LancamentosClassificados classificados) {

    public HistoricoFinanceiro {
        lancamentos = List.copyOf(lancamentos);
        isencoes = List.copyOf(isencoes);
        avisos = List.copyOf(avisos);
    }
}
