package br.gov.mt.sema.vistas.documento.application.port;

/**
 * O que a conversão produziu, para o item do dossiê (sumário) e o log, sem nenhum dado do documento.
 *
 * @param paginas páginas do PDF gerado
 * @param abasEmPaginaUnica abas de planilha que, no modo LARGURA, ficariam abaixo da escala mínima e foram para uma
 *     página única (PAGINA_POR_ABA só naquela aba); 0 para textos e no modo PAGINA_POR_ABA
 */
public record ResultadoDaConversao(int paginas, int abasEmPaginaUnica) {

    /** Observação para o sumário ou nula quando não há o que registrar. */
    public String observacao() {
        return abasEmPaginaUnica == 0 ? null
                : abasEmPaginaUnica + " aba(s) em página única: o ajuste à largura ficaria abaixo da escala mínima";
    }
}
