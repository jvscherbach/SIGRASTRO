package br.gov.mt.sema.rastro.documento.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Regras da lista: deduplicação (RN05), exclusões (RN11) e ordem cronológica (RN03). */
public final class CatalogoDeDocumentos {

    private CatalogoDeDocumentos() {
    }

    /**
     * Mesma chave válida em mais de uma origem aparece uma vez; vence a primeira origem da ordem de {@link OrigemDocumento}.
     * Chave "0" (ou vazia) nunca deduplica. Compactados nunca aparecem (RN11); geometrias ficam classificadas e são
     * filtradas pelo chamador (seção própria na S4). Resultado em ordem cronológica, sem data ao final.
     */
    public static List<Documento> consolidar(List<Documento> brutos) {
        List<Documento> porPrecedencia = new ArrayList<>(brutos);
        porPrecedencia.sort(Comparator.comparing((Documento d) -> d.origem().ordinal()).thenComparing(Documento::id, Documento.ORDEM_DE_ID));

        Set<String> chavesVistas = new HashSet<>();
        List<Documento> unicos = new ArrayList<>();
        for (Documento documento : porPrecedencia) {
            if (documento.tipoArquivo() == TipoArquivo.COMPACTADO) {
                continue;
            }
            if (documento.temChaveValida() && !chavesVistas.add(documento.chaveDoArquivo())) {
                continue;
            }
            unicos.add(documento);
        }
        unicos.sort(Documento.ORDEM_CRONOLOGICA);
        return unicos;
    }
}
