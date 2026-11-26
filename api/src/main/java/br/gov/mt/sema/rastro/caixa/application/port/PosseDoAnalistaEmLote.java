package br.gov.mt.sema.rastro.caixa.application.port;

import java.time.Duration;
import java.util.List;

import br.gov.mt.sema.rastro.caixa.domain.AnalistaParaLeitura;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.LeituraDaPosse;

/**
 * Leitura da posse dos analistas no SIGA e no Camunda (S11, bloco 4), em duas consultas por lote, sem laço por linha. Só a
 * atualização do instantâneo chama, sempre por {@code LerPosseEmLotes} (lotes de até {@value #MAXIMO_POR_LOTE} e limite de
 * leituras simultâneas do Camunda). Somente leitura. O CPF dos analistas é parâmetro da consulta e nunca volta: o retorno só
 * traz a chave opaca.
 */
public interface PosseDoAnalistaEmLote {

    /** Teto de itens por consulta ({@code IN}; o Oracle aceita até 1000). */
    int MAXIMO_POR_LOTE = 500;

    /**
     * Ids dos requerimentos que podem estar na posse de algum dos analistas: com tarefa ativa de análise ou revisão deles,
     * ou em correção cuja última análise ou revisão concluída foi deles. Em ordem de id, no máximo {@code maximo}.
     *
     * @param analistas até {@value #MAXIMO_POR_LOTE}
     */
    List<Long> candidatos(List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao, int maximo, Duration tempoLimite);

    /**
     * Detalhe dos requerimentos: tarefas ativas, última tarefa de análise ou revisão, interessado e tipo.
     *
     * @param ids       até {@value #MAXIMO_POR_LOTE} ids distintos (mais do que isso é recusado)
     * @param analistas até {@value #MAXIMO_POR_LOTE}: quem tem o responsável identificado na resposta
     */
    LeituraDaPosse ler(List<Long> ids, List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao,
            Duration tempoLimite);
}
