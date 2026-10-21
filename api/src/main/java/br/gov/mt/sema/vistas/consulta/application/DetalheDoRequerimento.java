package br.gov.mt.sema.vistas.consulta.application;

import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.vistas.consulta.domain.Requerimento;
import br.gov.mt.sema.vistas.consulta.domain.TarefaAtual;

public record DetalheDoRequerimento(Requerimento requerimento, ObjetoDoRequerimento objeto,
        PrioridadeAtendimento prioridade, TarefaAtual tarefaAtual) {
}
