package br.gov.mt.sema.rastro.consulta.application;

import br.gov.mt.sema.rastro.consulta.domain.TarefaAtual;

/** Porta de leitura da tarefa atual do fluxo do requerimento, implementada por integration.siga. */
public interface TarefaAtualDoRequerimento {

    TarefaAtual obter(long idRequerimento);
}
