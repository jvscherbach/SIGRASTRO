package br.gov.mt.sema.rastro.caixa.application.port;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.EstadoDePosse;

/**
 * O "último visto" de cada analista (S11, bloco 5): requerimento para estado de posse, gravado quando ele abre a lista de
 * mudanças. Hoje em memória ({@code caixa.infrastructure.memoria}; some ao reiniciar, e reinício vale como primeiro acesso,
 * sem aviso retroativo); a H1 troca por banco sem mexer nas regras. As operações de escrita são atômicas por analista: duas
 * abas abertas ao mesmo tempo não se quebram.
 */
public interface VistoDoAnalista {

    /** O último visto do analista; vazio = nunca houve (primeiro acesso). */
    Optional<Map<Long, EstadoDePosse>> ultimo(ChaveDoResponsavel analista);

    /** Grava só se o analista ainda não tem visto. {@code true} = gravou (era o primeiro acesso). */
    boolean gravarSeAusente(ChaveDoResponsavel analista, Map<Long, EstadoDePosse> estados);

    /** Troca o visto pelo resultado da regra, que recebe o atual (vazio se não houver), de forma atômica. */
    void atualizar(ChaveDoResponsavel analista, UnaryOperator<Map<Long, EstadoDePosse>> regra);
}
