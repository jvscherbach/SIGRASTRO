package br.gov.mt.sema.vistas.consulta.application;

import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.PrioridadeAtendimento;

/**
 * Porta de leitura do objeto (objetivos, finalidades, atividades) e da prioridade do requerimento, implementada
 * por integration.siga. Falha de leitura propaga como SIGA_INDISPONIVEL (503) ou erro interno (500): o detalhe
 * sai completo ou dá erro.
 */
public interface ObjetoEPrioridadeDoRequerimento {

    ObjetoDoRequerimento objeto(long idRequerimento);

    PrioridadeAtendimento prioridade(long idRequerimento);
}
