package br.gov.mt.sema.rastro.painel.application.port;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import br.gov.mt.sema.rastro.painel.domain.LeituraDasTarefas;

/**
 * Tarefas ativas das instâncias raiz de um lote de requerimentos (S10, bloco 2), numa só consulta ao Camunda, com o
 * <b>nome</b> do responsável já resolvido (o CPF nunca sai). Só a atualização do instantâneo chama, sempre por
 * {@code LerTarefasEmLotes} (lotes de até {@value #MAXIMO_POR_LOTE} e limite de leituras simultâneas do Camunda).
 */
public interface TarefasAtivasEmLote {

    /** Teto de ids por consulta ({@code BUSINESS_KEY_ IN}; o Oracle aceita até 1000). */
    int MAXIMO_POR_LOTE = 500;

    /**
     * @param idsRequerimento   até {@value #MAXIMO_POR_LOTE} ids distintos (mais do que isso é recusado)
     * @param chavesDosModelos  chaves de definição dos modelos do catálogo de etapas: só instâncias raiz desses modelos
     *                          entram (a complementação e outros processos com a mesma chave de negócio ficam de fora)
     * @param tempoLimite       tempo limite da consulta (arredondado para cima, no mínimo 1 s)
     */
    LeituraDasTarefas ler(List<Long> idsRequerimento, Collection<String> chavesDosModelos, Duration tempoLimite);
}
