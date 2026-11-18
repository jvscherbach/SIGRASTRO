package br.gov.mt.sema.vistas.auditoria.domain;

/** Uma linha do antes e depois de {@code USUARIO} e {@code CONFIG}. A construção só limpa e nunca lança. */
public record AlteracaoDoEvento(String item, String valorAnterior, String valorNovo) {

    private static final int MAX_ITEM = 200;
    private static final int MAX_VALOR = 500;

    public AlteracaoDoEvento {
        item = TextoDoEvento.limpar(item, MAX_ITEM);
        valorAnterior = TextoDoEvento.limpar(valorAnterior, MAX_VALOR);
        valorNovo = TextoDoEvento.limpar(valorNovo, MAX_VALOR);
    }
}
