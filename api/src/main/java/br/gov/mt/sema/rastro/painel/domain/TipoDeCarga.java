package br.gov.mt.sema.rastro.painel.domain;

/** Natureza de uma linha do gráfico 3. A ordem é a "posição fixa" do desempate. */
public enum TipoDeCarga {
    SERVIDOR(true),
    AREA(false),
    INTERESSADO(false),
    NAO_ATRIBUIDA(false),
    NAO_IDENTIFICADO(false),
    OUTROS(true);

    private final boolean comNome;

    TipoDeCarga(boolean comNome) {
        this.comNome = comNome;
    }

    /** Linhas que revelam nome de servidor (ou a existência de vários): somem com {@code mostrarNome} falso. */
    public boolean comNome() {
        return comNome;
    }
}
