package br.gov.mt.sema.vistas.consulta.domain;

/** Tipos de outorga do SIGA (P01). Valor desconhecido mantém o texto cru. */
public enum TipoOutorga {
    SUBTERRANEA("Subterrânea"),
    SUPERFICIAL("Superficial"),
    BARRAMENTO("Barramento");

    private final String descricao;

    TipoOutorga(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }

    /** Descrição de exibição do valor gravado no SIGA; o texto cru quando não é um valor conhecido. */
    public static String descricaoDe(String valorDoSiga) {
        if (valorDoSiga == null || valorDoSiga.isBlank()) {
            return null;
        }
        String valor = valorDoSiga.trim();
        for (TipoOutorga tipo : values()) {
            if (tipo.name().equals(valor)) {
                return tipo.descricao;
            }
        }
        return valor;
    }
}
