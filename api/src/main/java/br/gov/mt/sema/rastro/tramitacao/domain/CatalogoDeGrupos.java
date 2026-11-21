package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.Optional;

/**
 * Porta do catálogo de grupos (arquivo {@code rastro/grupos.yaml}, validado na subida): id do grupo candidato do Camunda
 * → rótulo da área ("Conferência de documentos (subterrânea)"). Nunca lança.
 */
public interface CatalogoDeGrupos {

    /** Grupo que não tem área: valor {@code 0} gravado pelo SIGA quando não há grupo (regra técnica 13). */
    String GRUPO_SEM_AREA = "0";

    /** Rótulo da área; vazio para grupo desconhecido, nulo, em branco ou {@value #GRUPO_SEM_AREA}. */
    Optional<String> area(String idDoGrupo);
}
