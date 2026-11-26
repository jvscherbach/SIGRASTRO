package br.gov.mt.sema.rastro.caixa.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regras do aviso de recebimento (S11, bloco 5; domínio puro). Há aviso quando o estado atual é {@code COM_ANALISTA} e o do
 * último visto era {@code FORA}, {@code COM_INTERESSADO} ou inexistente. Mudar de etapa dentro da posse não avisa; sair da
 * posse também não.
 */
public final class RegrasDoAviso {

    private RegrasDoAviso() {
    }

    /** Os estados atuais por requerimento: é o que o "visto" guarda. */
    public static Map<Long, EstadoDePosse> estados(List<ItemDaCaixa> itens) {
        Map<Long, EstadoDePosse> estados = new HashMap<>();
        itens.forEach(i -> estados.put(i.id(), i.estado()));
        return estados;
    }

    /** O item é de um requerimento que chegou desde o último visto. */
    public static boolean chegou(ItemDaCaixa item, Map<Long, EstadoDePosse> visto) {
        if (!item.estado().comAnalista()) {
            return false;
        }
        EstadoDePosse antes = visto.get(item.id());
        return antes == null || !antes.comAnalista();
    }

    /** Os itens avisados, na ordem recebida. */
    public static List<ItemDaCaixa> avisados(List<ItemDaCaixa> itens, Map<Long, EstadoDePosse> visto) {
        return itens.stream().filter(i -> chegou(i, visto)).toList();
    }

    /**
     * O visto depois de o analista abrir a lista de mudanças: o estado atual de tudo, exceto o que chegou e <b>não</b> estava
     * no conjunto que ele viu ({@code idsVistos}), que continua como estava (e avisa de novo). Itens que não estão mais na
     * caixa saem do visto: voltando depois, avisam.
     */
    public static Map<Long, EstadoDePosse> aposAbrirALista(List<ItemDaCaixa> itens, Map<Long, EstadoDePosse> visto,
            Set<Long> idsVistos) {
        Map<Long, EstadoDePosse> novo = new HashMap<>();
        for (ItemDaCaixa item : itens) {
            if (chegou(item, visto) && !idsVistos.contains(item.id())) {
                EstadoDePosse antes = visto.get(item.id());
                if (antes != null) {
                    novo.put(item.id(), antes);
                }
            } else {
                novo.put(item.id(), item.estado());
            }
        }
        return novo;
    }
}
