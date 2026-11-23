package br.gov.mt.sema.rastro.financeiro.domain;

/**
 * De onde veio a data do "Isento de Taxa" (S9, bloco 4; cadeia de apoio provisória em {@link DataDaIsencao}). Fora de
 * {@link #LANCAMENTO}, a tela mostra a nota "data a confirmar".
 */
public enum OrigemDaData {
    /** {@code DATA_EMISSAO} do próprio lançamento {@code ISENTA}. */
    LANCAMENTO,
    /** Entrada da passagem "Isenção confirmada" da tramitação (S8). */
    FLUXO,
    /** Nenhuma fonte tinha a data: "data não registrada". */
    AUSENTE
}
