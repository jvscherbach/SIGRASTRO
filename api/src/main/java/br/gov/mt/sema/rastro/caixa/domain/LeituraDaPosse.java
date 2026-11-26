package br.gov.mt.sema.rastro.caixa.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Posse lida de um ou mais lotes (S11, bloco 4).
 *
 * @param requerimentos um por requerimento, na ordem do id
 * @param incompleta    passou do teto de requerimentos ou do teto de linhas de algum lote: a leitura é parcial e a tela diz
 */
public record LeituraDaPosse(List<PosseLida> requerimentos, boolean incompleta) {

    public static final LeituraDaPosse NENHUMA = new LeituraDaPosse(List.of(), false);

    public LeituraDaPosse {
        requerimentos = List.copyOf(requerimentos);
    }

    /** Junta as leituras, na ordem; o mesmo requerimento em duas leituras vale uma vez (a primeira). */
    public static LeituraDaPosse juntar(Collection<LeituraDaPosse> leituras) {
        Map<Long, PosseLida> unicos = new LinkedHashMap<>();
        boolean incompleta = false;
        for (LeituraDaPosse leitura : leituras) {
            incompleta |= leitura.incompleta();
            leitura.requerimentos().forEach(p -> unicos.putIfAbsent(p.idRequerimento(), p));
        }
        return new LeituraDaPosse(List.copyOf(unicos.values()), incompleta);
    }
}
