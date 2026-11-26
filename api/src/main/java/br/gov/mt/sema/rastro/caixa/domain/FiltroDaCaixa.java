package br.gov.mt.sema.rastro.caixa.domain;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Filtro e ordem da lista da caixa (S11, bloco 5; domínio puro). Etapa: id de uma etapa de posse, ou o da correção (itens com
 * o interessado); vazio = visão geral. Busca por parte do nome do interessado ou do número, sem diferenciar maiúsculas nem
 * acentos. Ordem padrão: mais tempo parado primeiro; quem não tem início da etapa ("sem informação") vai por último.
 */
public final class FiltroDaCaixa {

    /** Ordem da lista. */
    public enum Ordem { MAIS_PARADO, MAIS_RECENTE }

    private FiltroDaCaixa() {
    }

    public static List<ItemDaCaixa> aplicar(List<ItemDaCaixa> itens, Optional<String> etapa, String busca, Ordem ordem) {
        String termo = normalizar(busca);
        Comparator<ItemDaCaixa> porInicio = Comparator.comparing(
                (ItemDaCaixa i) -> i.inicioDaEtapa().orElse(LocalDateTime.MAX));
        Comparator<ItemDaCaixa> ordenacao = (ordem == Ordem.MAIS_RECENTE
                ? Comparator.comparing((ItemDaCaixa i) -> i.inicioDaEtapa().isEmpty()).thenComparing(porInicio.reversed())
                : Comparator.comparing((ItemDaCaixa i) -> i.inicioDaEtapa().isEmpty()).thenComparing(porInicio))
                .thenComparingLong(ItemDaCaixa::id);
        return itens.stream()
                .filter(i -> etapa.isEmpty() || daEtapa(i, etapa.get()))
                .filter(i -> termo.isEmpty() || contem(i, termo))
                .sorted(ordenacao)
                .toList();
    }

    private static boolean daEtapa(ItemDaCaixa item, String etapa) {
        if (etapa.equals(DefinicaoDaPosse.ETAPA_CORRECAO)) {
            return !item.estado().comAnalista();
        }
        return item.estado().etapa().filter(etapa::equals).isPresent();
    }

    private static boolean contem(ItemDaCaixa item, String termo) {
        return item.interessado().map(FiltroDaCaixa::normalizar).filter(n -> n.contains(termo)).isPresent()
                || item.numero().map(FiltroDaCaixa::normalizar).filter(n -> n.contains(termo)).isPresent();
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
