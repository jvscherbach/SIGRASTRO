package br.gov.mt.sema.rastro.caixa.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.ResolucaoDeEtapa;

/**
 * Decide o estado de posse (S11, bloco 5; domínio puro). Um requerimento está na posse de um analista quando:
 * <ul>
 *   <li>tem tarefa ativa dele em "Análise do processo" ou "Revisão do parecer técnico" (etapas pelo catálogo): {@code COM_ANALISTA};</li>
 *   <li>ou está em "Correção do requerimento" (com o interessado) e a última tarefa de análise ou revisão foi dele:
 *       {@code COM_INTERESSADO}.</li>
 * </ul>
 * Qualquer outro caso é {@code FORA} e não gera item. Tarefa de grupo ainda não assumida não é de ninguém (fica para depois).
 */
public final class DecidirPosse {

    private DecidirPosse() {
    }

    /** Os itens de cada analista, na ordem do id. Só entram analistas que têm algo na posse. */
    public static Map<ChaveDoResponsavel, List<ItemDaCaixa>> agrupar(Collection<PosseLida> lidas, DefinicaoDaPosse definicao,
            CatalogoDeEtapas catalogo) {
        Map<ChaveDoResponsavel, List<ItemDaCaixa>> porAnalista = new LinkedHashMap<>();
        for (PosseLida posse : lidas) {
            Set<ChaveDoResponsavel> candidatos = new LinkedHashSet<>();
            posse.tarefasAtivas().forEach(t -> t.responsavel().ifPresent(candidatos::add));
            posse.ultimaDeAnalise().flatMap(PosseLida.UltimaTarefaDeAnalise::responsavel).ifPresent(candidatos::add);
            for (ChaveDoResponsavel chave : candidatos) {
                decidir(posse, chave, definicao, catalogo)
                        .ifPresent(item -> porAnalista.computeIfAbsent(chave, k -> new ArrayList<>()).add(item));
            }
        }
        porAnalista.values().forEach(lista -> lista.sort(Comparator.comparingLong(ItemDaCaixa::id)));
        return porAnalista;
    }

    /** O item do requerimento na caixa do analista, ou vazio se está fora da posse dele. */
    public static Optional<ItemDaCaixa> decidir(PosseLida posse, ChaveDoResponsavel analista, DefinicaoDaPosse definicao,
            CatalogoDeEtapas catalogo) {
        if (!definicao.chavesDosModelos().contains(posse.chaveDoModelo())) {
            return Optional.empty();
        }
        Optional<ItemDaCaixa> naPosse = posse.tarefasAtivas().stream()
                .filter(t -> definicao.atividadesDePosse().contains(t.atividade()))
                .filter(t -> t.responsavel().filter(analista::equals).isPresent())
                .flatMap(t -> etapaDePosse(posse, t.atividade(), catalogo)
                        .map(etapa -> new Achado(etapa, t.inicio())).stream())
                .min(Comparator.comparing((Achado a) -> a.inicio() == null ? LocalDateTime.MAX : a.inicio()))
                .map(a -> item(posse, EstadoDePosse.comAnalista(a.etapa()), a.inicio()));
        if (naPosse.isPresent()) {
            return naPosse;
        }
        boolean ultimaFoiDele = posse.ultimaDeAnalise()
                .filter(u -> definicao.atividadesDePosse().contains(u.atividade()))
                .flatMap(PosseLida.UltimaTarefaDeAnalise::responsavel).filter(analista::equals).isPresent();
        if (!ultimaFoiDele) {
            return Optional.empty();
        }
        return posse.tarefasAtivas().stream()
                .filter(t -> definicao.atividadesDeCorrecao().contains(t.atividade()))
                .map(t -> Optional.ofNullable(t.inicio()))
                .min(Comparator.comparing((Optional<LocalDateTime> i) -> i.orElse(LocalDateTime.MAX)))
                .map(inicio -> item(posse, EstadoDePosse.COM_INTERESSADO, inicio.orElse(null)));
    }

    private record Achado(String etapa, LocalDateTime inicio) {
    }

    private static Optional<String> etapaDePosse(PosseLida posse, String atividade, CatalogoDeEtapas catalogo) {
        if (catalogo.resolver(posse.chaveDoModelo(), atividade, Set.of()) instanceof ResolucaoDeEtapa.NaEtapa na
                && DefinicaoDaPosse.ETAPAS_DE_POSSE.contains(na.etapa().id())) {
            return Optional.of(na.etapa().id());
        }
        return Optional.empty();
    }

    private static ItemDaCaixa item(PosseLida posse, EstadoDePosse estado, LocalDateTime inicio) {
        return new ItemDaCaixa(posse.idRequerimento(), posse.numero(), posse.interessado(), posse.tipo(), estado,
                Optional.ofNullable(inicio));
    }
}
