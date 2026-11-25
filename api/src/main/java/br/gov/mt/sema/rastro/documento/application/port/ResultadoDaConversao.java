package br.gov.mt.sema.rastro.documento.application.port;

/**
 * O que a conversão produziu, para o item do dossiê (sumário) e o log, sem nenhum dado do documento.
 *
 * @param paginas páginas do PDF gerado
 * @param abasEmPaginaUnica abas de planilha que, no modo LARGURA, ficariam abaixo da escala mínima e foram para uma
 *     página única (PAGINA_POR_ABA só naquela aba); 0 para textos e no modo PAGINA_POR_ABA
 * @param graficosOmitidos planilha convertida no perfil estrito (pacote com conteúdo ativo) que tinha gráficos ou
 *     desenhos: o PDF não os traz (V6)
 */
public record ResultadoDaConversao(int paginas, int abasEmPaginaUnica, boolean graficosOmitidos) {

    public ResultadoDaConversao(int paginas, int abasEmPaginaUnica) {
        this(paginas, abasEmPaginaUnica, false);
    }

    /** Observação para o sumário ou nula quando não há o que registrar. */
    public String observacao() {
        String texto = abasEmPaginaUnica == 0 ? null
                : abasEmPaginaUnica + " aba(s) em página única: o ajuste à largura ficaria abaixo da escala mínima";
        if (!graficosOmitidos) {
            return texto;
        }
        String aviso = "os gráficos desta planilha não foram incluídos";
        return texto == null ? aviso : texto + "; " + aviso;
    }
}
