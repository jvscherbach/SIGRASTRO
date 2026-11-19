package br.gov.mt.sema.rastro.consulta.application;

import java.util.Optional;

import br.gov.mt.sema.rastro.consulta.domain.CriterioBusca;
import br.gov.mt.sema.rastro.consulta.domain.NumeroRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Pagina;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.ResumoRequerimento;

/**
 * Porta de leitura de requerimentos, implementada por integration.siga. O número do requerimento é exato (1
 * resultado); o número do processo pode repetir (1:N) e por isso é buscado pela lista de critérios.
 */
public interface RequerimentosDoSiga {

    Optional<Requerimento> buscarPorNumeroDeRequerimento(NumeroRequerimento numero);

    Optional<Requerimento> buscarPorId(long id);

    /** Aplica todos os critérios, inclusive a restrição por interessado, na própria consulta. */
    Pagina<ResumoRequerimento> buscarPorCriterios(CriterioBusca criterio);
}
