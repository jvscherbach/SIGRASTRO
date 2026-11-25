package br.gov.mt.sema.rastro.painel.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tarefas ativas de um ou mais lotes de requerimentos (S10, bloco 2), cruas.
 *
 * @param tarefas         tarefas ativas das instâncias raiz, no modelo do catálogo
 * @param comInstancia    requerimentos com instância raiz <b>ativa</b> num modelo do catálogo, com ou sem tarefa (quem não
 *                        está aqui não tem instância ativa: "Sem instância")
 * @param alemDoLimite    algum lote passou do teto de linhas da consulta: as tarefas daquele lote estão incompletas
 */
public record LeituraDasTarefas(List<TarefaAtiva> tarefas, Set<Long> comInstancia, boolean alemDoLimite) {

    public static final LeituraDasTarefas NENHUMA = new LeituraDasTarefas(List.of(), Set.of(), false);

    public LeituraDasTarefas {
        tarefas = List.copyOf(tarefas);
        comInstancia = Set.copyOf(comInstancia);
    }

    /** Junta as leituras de vários lotes, na ordem. */
    public static LeituraDasTarefas juntar(Collection<LeituraDasTarefas> lotes) {
        List<TarefaAtiva> tarefas = new ArrayList<>();
        Set<Long> comInstancia = new LinkedHashSet<>();
        boolean alem = false;
        for (LeituraDasTarefas lote : lotes) {
            tarefas.addAll(lote.tarefas());
            comInstancia.addAll(lote.comInstancia());
            alem |= lote.alemDoLimite();
        }
        return new LeituraDasTarefas(tarefas, comInstancia, alem);
    }
}
