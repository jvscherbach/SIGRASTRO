package br.gov.mt.sema.rastro.financeiro.application.port;

import java.util.Collection;
import java.util.Map;

import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;

/**
 * Leitura dos lançamentos de taxa ({@code SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA}, S9, bloco 2). Implementada só em
 * {@code integration.siga}, por um único componente de acesso (risco R04): só {@code SELECT}, com parâmetros nomeados,
 * limite por requerimento e tempo limite por consulta. Nada é interpretado ali; a regra de validade é do domínio.
 * Falha do banco sobe como exceção de acesso a dados, para o caso de uso tratar.
 */
public interface LancamentosDoRequerimento {

    /** Teto de requerimentos numa consulta em lote ({@code IN}; o Oracle aceita até 1000). */
    int MAXIMO_POR_LOTE = 500;

    /** Uma consulta. Sem lançamento, {@link LeituraDosLancamentos#NENHUM}. */
    LeituraDosLancamentos lerDoRequerimento(long idRequerimento);

    /**
     * Uma consulta a cada {@value #MAXIMO_POR_LOTE} requerimentos (ids repetidos contam uma vez), sem laço por linha. Todo
     * id pedido aparece no mapa, com {@link LeituraDosLancamentos#NENHUM} quando não tem lançamento.
     */
    Map<Long, LeituraDosLancamentos> lerEmLote(Collection<Long> idsRequerimento);
}
