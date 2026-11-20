package br.gov.mt.sema.rastro.tramitacao.domain;

/** Natureza da etapa, no vocabulário do usuário (docs/15). {@code FINAL} só aparece nos finais do modelo. */
public enum TipoDeEtapa {
    TAREFA_HUMANA,
    ESPERA,
    AUTOMATICA,
    EMISSAO,
    FINAL
}
