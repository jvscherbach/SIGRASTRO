package br.gov.mt.sema.rastro.painel.domain;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeGrupos;
import br.gov.mt.sema.rastro.tramitacao.domain.ResolucaoDeEtapa;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Gráfico 3 (função pura, nunca lança): quantos requerimentos distintos cada responsável tem na etapa atual.
 * Só entram requerimentos da fila (fora de concluído, cancelado e arquivado) e tarefas que o catálogo conhece como etapa
 * ou não conhece (as de suporte e de fim não são posição). Os 15 maiores servidores aparecem; o resto vira
 * "Outros (N servidores)". Com {@code mostrarNome} falso, só as categorias sem nome (RN12). Empate: nome, depois posição fixa.
 */
public final class ContarCargaPorResponsavel {

    public static final int MAXIMO_DE_SERVIDORES = 15;
    public static final String ROTULO_INTERESSADO = "Interessado (cadastrante)";
    public static final String ROTULO_NAO_ATRIBUIDA = "Não atribuída";
    public static final String ROTULO_NAO_IDENTIFICADO = "Não identificado";
    public static final String ROTULO_AREA_SEM_CADASTRO = "Equipe responsável";
    public static final String NOTA_NOMES_OCULTOS = "Nomes ocultos por configuração";

    private static final Comparator<LinhaDeCarga> ORDEM = Comparator
            .<LinhaDeCarga>comparingLong(l -> -l.total())
            .thenComparing(LinhaDeCarga::rotulo, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(LinhaDeCarga::rotulo)
            .thenComparing(l -> l.tipo().ordinal());

    private ContarCargaPorResponsavel() {
    }

    public static CargaPorResponsavel contar(CatalogoDeEtapas catalogo, CatalogoDeGrupos grupos,
            List<RequerimentoDaBase> base, LeituraDasTarefas leitura, boolean mostrarNome) {
        Map<Chave, Set<Long>> porLinha = agrupar(catalogo, grupos, base, leitura);
        List<LinhaDeCarga> linhas = new ArrayList<>();
        List<Map.Entry<Chave, Set<Long>>> servidores = new ArrayList<>();
        for (Map.Entry<Chave, Set<Long>> e : porLinha.entrySet()) {
            if (e.getKey().tipo().comNome()) {
                servidores.add(e);
            } else {
                linhas.add(new LinhaDeCarga(e.getKey().tipo(), e.getKey().rotulo(), e.getValue().size()));
            }
        }
        if (!mostrarNome) {
            linhas.sort(ORDEM);
            return new CargaPorResponsavel(linhas, true,
                    List.of(new AvisoDoPainel(AvisoDoPainel.NOMES_OCULTOS, NOTA_NOMES_OCULTOS)));
        }
        servidores.sort(Comparator
                .<Map.Entry<Chave, Set<Long>>>comparingInt(e -> -e.getValue().size())
                .thenComparing(e -> e.getKey().rotulo(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.getKey().rotulo()));
        Set<Long> restoDistinto = new HashSet<>();
        int restantes = 0;
        for (int i = 0; i < servidores.size(); i++) {
            var e = servidores.get(i);
            if (i < MAXIMO_DE_SERVIDORES) {
                linhas.add(new LinhaDeCarga(TipoDeCarga.SERVIDOR, e.getKey().rotulo(), e.getValue().size()));
            } else {
                restantes++;
                restoDistinto.addAll(e.getValue());
            }
        }
        linhas.sort(ORDEM);
        if (restantes > 0) {
            linhas.add(new LinhaDeCarga(TipoDeCarga.OUTROS, "Outros (" + restantes + " servidores)",
                    restoDistinto.size()));
        }
        return new CargaPorResponsavel(linhas, false, List.of());
    }

    /**
     * Requerimentos distintos por responsável (a regra única de quem é o responsável e de quais tarefas contam). A lista por
     * analista (S11, bloco 9) usa este mesmo agrupamento para que os totais batam com as barras.
     */
    static Map<Chave, Set<Long>> agrupar(CatalogoDeEtapas catalogo, CatalogoDeGrupos grupos,
            List<RequerimentoDaBase> base, LeituraDasTarefas leitura) {
        Set<Long> naFila = new HashSet<>();
        for (RequerimentoDaBase r : base) {
            if (!SituacoesForaDaFila.estaForaDaFila(r.situacao())) {
                naFila.add(r.id());
            }
        }
        Map<Chave, Set<Long>> porLinha = new LinkedHashMap<>();
        for (TarefaAtiva t : leitura.tarefas()) {
            if (!naFila.contains(t.idRequerimento()) || ehSuporteOuFim(catalogo, t)) {
                continue;
            }
            porLinha.computeIfAbsent(chaveDa(t, grupos), k -> new HashSet<>()).add(t.idRequerimento());
        }
        return porLinha;
    }

    static boolean ehSuporteOuFim(CatalogoDeEtapas catalogo, TarefaAtiva t) {
        ResolucaoDeEtapa r = catalogo.resolver(t.chaveDoModelo(), t.idDaTarefa(), Set.of());
        return r instanceof ResolucaoDeEtapa.Ignorado || r instanceof ResolucaoDeEtapa.Encerrado;
    }

    static Chave chaveDa(TarefaAtiva t, CatalogoDeGrupos grupos) {
        return switch (t.responsavel()) {
            case INTERESSADO -> new Chave(TipoDeCarga.INTERESSADO, ROTULO_INTERESSADO);
            case NAO_IDENTIFICADO -> new Chave(TipoDeCarga.NAO_IDENTIFICADO, ROTULO_NAO_IDENTIFICADO);
            case SERVIDOR -> t.nome().map(String::trim).filter(n -> !n.isEmpty())
                    .map(n -> new Chave(TipoDeCarga.SERVIDOR, n))
                    .orElseGet(() -> new Chave(TipoDeCarga.NAO_IDENTIFICADO, ROTULO_NAO_IDENTIFICADO));
            case NAO_ATRIBUIDA -> areaDe(t, grupos);
        };
    }

    private static Chave areaDe(TarefaAtiva t, CatalogoDeGrupos grupos) {
        TreeSet<String> ids = new TreeSet<>();
        for (String g : t.grupos()) {
            if (g != null && !g.isBlank() && !CatalogoDeGrupos.GRUPO_SEM_AREA.equals(g.trim())) {
                ids.add(g.trim());
            }
        }
        if (ids.isEmpty()) {
            return new Chave(TipoDeCarga.NAO_ATRIBUIDA, ROTULO_NAO_ATRIBUIDA);
        }
        for (String id : ids) {
            var area = grupos.area(id);
            if (area.isPresent()) {
                return new Chave(TipoDeCarga.AREA, area.get());
            }
        }
        return new Chave(TipoDeCarga.AREA, ROTULO_AREA_SEM_CADASTRO);
    }

    record Chave(TipoDeCarga tipo, String rotulo) {
    }
}
