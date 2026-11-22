package br.gov.mt.sema.rastro.financeiro.application;

import br.gov.mt.sema.rastro.financeiro.domain.HistoricoFinanceiro;

/**
 * O histórico financeiro de um requerimento como o usuário o recebe (S9, bloco 6): o que o {@link AbrirFinanceiro} montou
 * depois de conferir o acesso e aplicar as regras de exibição do perfil.
 *
 * @param historico      vazio ({@code null}) quando o estado é {@link Estado#INDISPONIVEL}
 * @param mostrarValores {@code RASTRO_MOSTRAR_VALORES}: desligado, valor, desconto e UPF não saem em nenhuma resposta
 * @param diagnostico    só o Administrador: ids técnicos dos lançamentos, ignorados por motivo e origem da data
 */
public record FinanceiroAberto(long idRequerimento, String numeroRequerimento, String numeroProcesso, Estado estado,
        HistoricoFinanceiro historico, boolean mostrarValores, boolean diagnostico) {

    public enum Estado {
        /** Lançamentos lidos (inclusive nenhum: {@code SEM_TAXA}). */
        ATIVO,
        /** Banco do SIGA fora do ar, lento ou não configurado: a tela degrada, sem 5xx. */
        INDISPONIVEL
    }
}
