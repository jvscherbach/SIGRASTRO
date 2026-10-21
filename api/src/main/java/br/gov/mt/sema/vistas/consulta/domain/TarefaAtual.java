package br.gov.mt.sema.vistas.consulta.domain;

import java.time.LocalDateTime;

/**
 * Tarefa atual do fluxo do requerimento; {@code responsavel} é nulo sem tarefa ativa ou quando o perfil não pode
 * vê-lo. {@code INDISPONIVEL} quando a leitura falhou: não derruba o detalhe.
 */
public record TarefaAtual(Estado estado, String nome, LocalDateTime desde, ResponsavelTarefa responsavel,
        String fluxo) {

    public enum Estado {
        ATIVA,
        SEM_TAREFA,
        INDISPONIVEL
    }

    public static TarefaAtual ativa(String nome, LocalDateTime desde, ResponsavelTarefa responsavel, String fluxo) {
        return new TarefaAtual(Estado.ATIVA, nome, desde, responsavel, fluxo);
    }

    public static TarefaAtual semTarefa() {
        return new TarefaAtual(Estado.SEM_TAREFA, null, null, null, null);
    }

    public static TarefaAtual indisponivel() {
        return new TarefaAtual(Estado.INDISPONIVEL, null, null, null, null);
    }

    /** Cópia sem o responsável: para quem não pode vê-lo, o campo é removido antes de sair do caso de uso. */
    public TarefaAtual semResponsavel() {
        return new TarefaAtual(estado, nome, desde, null, fluxo);
    }
}
