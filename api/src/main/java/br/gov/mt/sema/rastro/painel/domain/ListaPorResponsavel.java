package br.gov.mt.sema.rastro.painel.domain;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A lista de requerimentos por analista do painel do Gestor (S11, bloco 9), guardada no instantâneo do painel: sai da mesma
 * leitura e da mesma regra de agrupamento da carga por responsável, então o total de cada linha é o da barra. Nenhum campo de
 * CPF; o responsável é identificado pela chave opaca (HMAC com o sal do processo).
 *
 * @param analistas as linhas, na ordem das barras da carga, cada uma com a chave opaca
 * @param etapas    os rótulos de etapa que um requerimento da lista pode ter, na ordem do catálogo, mais "Etapa não mapeada"
 * @param porChave  os requerimentos de cada chave
 */
public record ListaPorResponsavel(List<LinhaDeAnalista> analistas, List<String> etapas,
        Map<String, List<RequerimentoDaLista>> porChave) {

    public static final ListaPorResponsavel VAZIA = new ListaPorResponsavel(List.of(), List.of(), Map.of());

    /** Uma linha da lista de analistas: o mesmo total da barra da carga. */
    public record LinhaDeAnalista(String chave, String rotulo, TipoDeCarga tipo, long total) {
        public LinhaDeAnalista {
            Objects.requireNonNull(chave);
            Objects.requireNonNull(rotulo);
            Objects.requireNonNull(tipo);
        }
    }

    /**
     * Um requerimento na lista de um analista.
     *
     * @param etapas      as etapas em que ele está <b>com este responsável</b> (paralelas: mais de uma)
     * @param responsavel quem tem a tarefa (o rótulo da linha, ou os servidores do grupo "Outros")
     */
    public record RequerimentoDaLista(long id, Optional<String> numero, Optional<String> interessado,
            Optional<LocalDate> protocolo, List<String> etapas, String responsavel) {
        public RequerimentoDaLista {
            Objects.requireNonNull(numero);
            Objects.requireNonNull(interessado);
            Objects.requireNonNull(protocolo);
            etapas = List.copyOf(etapas);
            Objects.requireNonNull(responsavel);
        }
    }

    public ListaPorResponsavel {
        analistas = List.copyOf(analistas);
        etapas = List.copyOf(etapas);
        porChave = Map.copyOf(porChave);
    }

    public Optional<LinhaDeAnalista> linha(String chave) {
        return analistas.stream().filter(l -> l.chave().equals(chave)).findFirst();
    }

    /** Os requerimentos de uma chave, filtrados por etapa (rótulo exato) e por busca (nome do interessado ou número). */
    public List<RequerimentoDaLista> requerimentos(String chave, Optional<String> etapa, String busca) {
        String termo = normalizar(busca);
        return porChave.getOrDefault(chave, List.of()).stream()
                .filter(r -> etapa.isEmpty() || r.etapas().contains(etapa.get()))
                .filter(r -> termo.isEmpty()
                        || r.interessado().map(ListaPorResponsavel::normalizar).filter(n -> n.contains(termo)).isPresent()
                        || r.numero().map(ListaPorResponsavel::normalizar).filter(n -> n.contains(termo)).isPresent())
                .toList();
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
