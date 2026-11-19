package br.gov.mt.sema.rastro.integration.siga.dto;

import java.time.LocalDateTime;

/**
 * Linha da consulta da tarefa atual. Tipo externo: não sai de integration.siga. Traz só a situação e o nome do
 * responsável; o CPF dele não é selecionado.
 */
public record TarefaLinha(String nome, LocalDateTime criadaEm, String fluxo, String situacaoResponsavel,
        String nomeResponsavel) {
}
