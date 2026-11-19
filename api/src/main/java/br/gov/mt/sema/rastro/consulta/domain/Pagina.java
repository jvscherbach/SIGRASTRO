package br.gov.mt.sema.rastro.consulta.domain;

import java.util.List;

public record Pagina<T>(List<T> itens, long total, int pagina, int tamanho) {

    public static <T> Pagina<T> vazia(int pagina, int tamanho) {
        return new Pagina<>(List.of(), 0, pagina, tamanho);
    }
}
