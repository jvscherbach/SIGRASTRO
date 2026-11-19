package br.gov.mt.sema.rastro.consulta.application;

import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.TarefaAtual;

public record DetalheDoRequerimento(Requerimento requerimento, ObjetoDoRequerimento objeto,
        PrioridadeAtendimento prioridade, TarefaAtual tarefaAtual) {
}
