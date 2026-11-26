package br.gov.mt.sema.rastro.painel.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import br.gov.mt.sema.rastro.painel.domain.ContarCargaPorResponsavel.Chave;
import br.gov.mt.sema.rastro.painel.domain.ListaPorResponsavel.LinhaDeAnalista;
import br.gov.mt.sema.rastro.painel.domain.ListaPorResponsavel.RequerimentoDaLista;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeGrupos;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.ResolucaoDeEtapa;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Lista de requerimentos por analista (S11, bloco 9; função pura). Parte da <b>carga por responsável já calculada</b>
 * ({@link ContarCargaPorResponsavel}): as linhas, a ordem, o corte dos 15 servidores e o "Outros" são os dela, e o
 * agrupamento dos requerimentos é o mesmo ({@code agrupar}), então o total de cada linha é exatamente o da barra. Com
 * {@code mostrarNome} falso só as categorias sem nome têm lista (um servidor nunca é alcançável). A chave opaca é um
 * HMAC-SHA256 do tipo e do rótulo com o sal do processo: não revela o rótulo nem serve fora desta execução.
 */
public final class ListarPorResponsavel {

    private static final String ALGORITMO = "HmacSHA256";
    private static final int CARACTERES_DA_CHAVE = 16;

    private ListarPorResponsavel() {
    }

    public static ListaPorResponsavel listar(CatalogoDeEtapas catalogo, CatalogoDeGrupos grupos,
            List<RequerimentoDaBase> base, LeituraDasTarefas leitura, CargaPorResponsavel carga, byte[] sal) {
        Map<Long, RequerimentoDaBase> porId = new HashMap<>();
        Set<Long> naFila = new HashSet<>();
        for (RequerimentoDaBase r : base) {
            porId.putIfAbsent(r.id(), r);
            if (!SituacoesForaDaFila.estaForaDaFila(r.situacao())) {
                naFila.add(r.id());
            }
        }
        Map<String, String> rotuloDoModelo = new HashMap<>();
        for (ModeloDeFluxo m : catalogo.modelos()) {
            rotuloDoModelo.put(m.chave(), m.rotulo());
        }
        // Chave de carga -> requerimento -> etapas (com este responsável), na ordem em que apareceram.
        Map<Chave, Map<Long, Set<String>>> etapasPorLinha = new LinkedHashMap<>();
        for (TarefaAtiva t : leitura.tarefas()) {
            if (!naFila.contains(t.idRequerimento()) || ContarCargaPorResponsavel.ehSuporteOuFim(catalogo, t)) {
                continue;
            }
            etapasPorLinha.computeIfAbsent(ContarCargaPorResponsavel.chaveDa(t, grupos), k -> new LinkedHashMap<>())
                    .computeIfAbsent(t.idRequerimento(), k -> new TreeSet<>())
                    .add(rotuloDaEtapa(catalogo, rotuloDoModelo, t));
        }
        Set<String> servidoresMostrados = new HashSet<>();
        for (LinhaDeCarga l : carga.linhas()) {
            if (l.tipo() == TipoDeCarga.SERVIDOR) {
                servidoresMostrados.add(l.rotulo());
            }
        }

        List<LinhaDeAnalista> analistas = new ArrayList<>();
        Map<String, List<RequerimentoDaLista>> porChave = new LinkedHashMap<>();
        for (LinhaDeCarga linha : carga.linhas()) {
            Map<Long, Set<String>> etapas = new LinkedHashMap<>();
            Map<Long, Set<String>> donos = new LinkedHashMap<>();
            for (Map.Entry<Chave, Map<Long, Set<String>>> e : etapasPorLinha.entrySet()) {
                if (!daLinha(linha, e.getKey(), servidoresMostrados)) {
                    continue;
                }
                e.getValue().forEach((id, rotulos) -> {
                    etapas.computeIfAbsent(id, k -> new TreeSet<>()).addAll(rotulos);
                    donos.computeIfAbsent(id, k -> new TreeSet<>()).add(e.getKey().rotulo());
                });
            }
            String chave = chaveOpaca(linha.tipo(), linha.rotulo(), sal);
            List<RequerimentoDaLista> requerimentos = new ArrayList<>();
            etapas.keySet().stream().sorted().forEach(id -> {
                RequerimentoDaBase r = porId.get(id);
                requerimentos.add(new RequerimentoDaLista(id,
                        r == null ? java.util.Optional.empty() : r.numero(),
                        r == null ? java.util.Optional.empty() : r.interessado(),
                        r == null ? java.util.Optional.empty() : r.protocolo(),
                        ordenarPorCatalogo(etapas.get(id), catalogo, rotuloDoModelo),
                        String.join(", ", donos.get(id))));
            });
            analistas.add(new LinhaDeAnalista(chave, linha.rotulo(), linha.tipo(), linha.total()));
            porChave.put(chave, requerimentos);
        }
        return new ListaPorResponsavel(analistas, todasAsEtapas(catalogo), porChave);
    }

    private static boolean daLinha(LinhaDeCarga linha, Chave chave, Set<String> servidoresMostrados) {
        if (linha.tipo() == TipoDeCarga.OUTROS) {
            return chave.tipo() == TipoDeCarga.SERVIDOR && !servidoresMostrados.contains(chave.rotulo());
        }
        return chave.tipo() == linha.tipo() && chave.rotulo().equals(linha.rotulo());
    }

    private static String rotuloDaEtapa(CatalogoDeEtapas catalogo, Map<String, String> rotuloDoModelo, TarefaAtiva t) {
        ResolucaoDeEtapa r = catalogo.resolver(t.chaveDoModelo(), t.idDaTarefa(), Set.of());
        if (r instanceof ResolucaoDeEtapa.NaEtapa na) {
            return rotuloDoModelo.getOrDefault(t.chaveDoModelo(), t.chaveDoModelo()) + " · " + na.etapa().rotulo();
        }
        return ResolucaoDeEtapa.ROTULO_NAO_MAPEADA;
    }

    /** Todos os rótulos que a lista pode ter, na ordem do catálogo (as barras), mais o das etapas não mapeadas. */
    static List<String> todasAsEtapas(CatalogoDeEtapas catalogo) {
        List<String> rotulos = new ArrayList<>();
        for (ModeloDeFluxo m : catalogo.modelos()) {
            for (var etapa : m.etapas()) {
                rotulos.add(m.rotulo() + " · " + etapa.rotulo());
            }
        }
        rotulos.add(ResolucaoDeEtapa.ROTULO_NAO_MAPEADA);
        return rotulos;
    }

    private static List<String> ordenarPorCatalogo(Set<String> rotulos, CatalogoDeEtapas catalogo,
            Map<String, String> rotuloDoModelo) {
        List<String> ordem = todasAsEtapas(catalogo);
        return new LinkedHashSet<>(rotulos).stream()
                .sorted(java.util.Comparator.comparingInt((String r) -> {
                    int i = ordem.indexOf(r);
                    return i < 0 ? Integer.MAX_VALUE : i;
                }).thenComparing(r -> r))
                .toList();
    }

    /** A chave opaca de uma linha: 16 caracteres hexadecimais do HMAC-SHA256 do tipo e do rótulo. */
    public static String chaveOpaca(TipoDeCarga tipo, String rotulo, byte[] sal) {
        if (sal == null || sal.length < 16) {
            throw new IllegalArgumentException("sal curto demais");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(sal, ALGORITMO));
            byte[] hash = mac.doFinal((tipo.name() + "|" + rotulo).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, CARACTERES_DA_CHAVE);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("não foi possível calcular a chave da linha");
        }
    }
}
