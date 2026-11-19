package br.gov.mt.sema.rastro.consulta.domain;

/** Situação lida do SIGA: o valor conhecido e o texto cru, que é exibido quando o valor é desconhecido. */
public record Situacao(SituacaoRequerimento tipo, String codigoOriginal) {

    public static Situacao de(String codigoDoSiga) {
        return new Situacao(SituacaoRequerimento.de(codigoDoSiga), codigoDoSiga);
    }

    public String codigo() {
        return tipo == SituacaoRequerimento.DESCONHECIDA ? textoCruOuPadrao() : tipo.name();
    }

    public String descricao() {
        return tipo == SituacaoRequerimento.DESCONHECIDA ? textoCruOuPadrao() : tipo.descricao();
    }

    private String textoCruOuPadrao() {
        return codigoOriginal == null || codigoOriginal.isBlank() ? tipo.descricao() : codigoOriginal;
    }
}
