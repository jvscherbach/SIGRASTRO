package br.gov.mt.sema.rastro.shared.api;

import java.util.List;

/**
 * Contrato público de página. {@code from(Page)} entra junto com o Spring Data, a partir da S1.
 */
public record PageResponse<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {

    public static <T> PageResponse<T> of(List<T> conteudo, int pagina, int tamanho, long totalElementos) {
        int totalPaginas = tamanho <= 0 ? 0 : (int) Math.ceil((double) totalElementos / tamanho);
        return new PageResponse<>(List.copyOf(conteudo), pagina, tamanho, totalElementos, totalPaginas);
    }
}
