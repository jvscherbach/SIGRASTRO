package br.gov.mt.sema.rastro.tramitacao.domain;

/** Papel de uma instância do Camunda no histórico de um requerimento (regras técnicas 1, 5 e 7). */
public enum OrigemDaInstancia {
    /** Instância raiz do requerimento ({@code BUSINESS_KEY_} = id do requerimento, sem instância-mãe). */
    RAIZ,
    /** Filha de {@code callActivity} (emissões): ligada pela instância-mãe, nunca pela business key. */
    FILHA,
    /** Complementação: instância independente e paralela ({@code procGestaoComplementacao}). */
    COMPLEMENTACAO
}
