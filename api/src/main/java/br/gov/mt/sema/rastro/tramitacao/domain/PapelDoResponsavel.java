package br.gov.mt.sema.rastro.tramitacao.domain;

/**
 * Papel de quem responde pela etapa no desenho do fluxo. Não é a pessoa: quem está com a tarefa agora é resolvido à parte
 * (bloco 4), sem expor CPF.
 */
public enum PapelDoResponsavel {
    INTERESSADO,
    ANALISTA,
    CHEFIA,
    GRUPO,
    SISTEMA
}
