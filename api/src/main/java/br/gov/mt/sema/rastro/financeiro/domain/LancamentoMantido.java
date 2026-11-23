package br.gov.mt.sema.rastro.financeiro.domain;

import java.util.Set;

/**
 * Lançamento que fica na lista: válido pela regra, ou mantido apesar de uma {@link Inconsistencia} (vazia = válido).
 */
public record LancamentoMantido(Lancamento lancamento, Set<Inconsistencia> inconsistencias) {

    public LancamentoMantido {
        inconsistencias = Set.copyOf(inconsistencias);
    }

    public boolean consistente() {
        return inconsistencias.isEmpty();
    }
}
