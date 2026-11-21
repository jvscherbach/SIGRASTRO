package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transforma os fatos crus do Camunda ({@link LeituraDoFluxo}) na posição atual, pelo catálogo (docs/15, "Posição
 * atual"). Função pura: sem banco, sem relógio, sem situação do Licenciamento.
 *
 * <ul>
 *   <li>Várias instâncias raiz: vale a mais recente por início (marca {@link MarcaDaPosicao#VARIAS_INSTANCIAS}).</li>
 *   <li>Encerrada: o final pelo {@code END_ACT_ID_}; fora do catálogo, encerrado sem final e marca de não mapeada.</li>
 *   <li>Ativa: cada tarefa (agrupada por id técnico; multi-instância vira um ponto com "x de y"), cada filha de emissão
 *       e cada execução parada fora de tarefa (espera confirmada por assinatura de evento, automática) vira um ponto.
 *       Espera sem assinatura não é confirmada: vira "não mapeada" em vez de suposição.</li>
 *   <li>Principal: a de menor ordem do ramo principal; sem ela, a de menor ordem da correção; sem nenhuma mapeada, a não
 *       mapeada mais antiga. As demais são "em paralelo". A complementação fica à parte.</li>
 *   <li>Tarefa "Suporte" ativa não é posição: sem outro ponto mapeado, a principal é a última etapa real percorrida.</li>
 * </ul>
 */
public final class MontadorDaPosicao {

    static final String CORPO_MULTI_INSTANCIA = "#multiInstanceBody";

    /** Empate (ex.: duas filhas no mesmo instante) mantém a ordem da leitura, que o SQL já fixa: a ordenação é estável. */
    private static final Comparator<ElementoDaPosicao> ORDEM_DOS_PONTOS = Comparator
            .comparingInt(MontadorDaPosicao::prioridade)
            .thenComparing(ElementoDaPosicao::desde, Comparator.nullsLast(Comparator.naturalOrder()));

    private MontadorDaPosicao() {
    }

    public static PosicaoAtual montar(ModeloDeFluxo modelo, LeituraDoFluxo leitura) {
        Objects.requireNonNull(modelo);
        Optional<LeituraDoFluxo.InstanciaRaiz> maisRecente = leitura.maisRecente();
        if (maisRecente.isEmpty()) {
            return PosicaoAtual.semInstancia(modelo);
        }
        LeituraDoFluxo.InstanciaRaiz raiz = maisRecente.get();
        Set<MarcaDaPosicao> marcas = EnumSet.noneOf(MarcaDaPosicao.class);
        Set<String> naoMapeados = new LinkedHashSet<>();
        if (leitura.instancias().size() > 1) {
            marcas.add(MarcaDaPosicao.VARIAS_INSTANCIAS);
        }
        Optional<String> diagnostico = Optional.of("STATE_=" + raiz.estado()
                + (raiz.comMotivoDeExclusao() ? "; com motivo de exclusão" : ""));
        if (raiz.encerrada()) {
            return encerrada(modelo, raiz, marcas, naoMapeados, diagnostico);
        }
        return new Ativa(modelo, leitura, raiz, marcas, naoMapeados).montar(diagnostico);
    }

    private static PosicaoAtual encerrada(ModeloDeFluxo modelo, LeituraDoFluxo.InstanciaRaiz raiz,
            Set<MarcaDaPosicao> marcas, Set<String> naoMapeados, Optional<String> diagnostico) {
        Optional<FinalDoFluxo> fim = Optional.empty();
        if (modelo.resolver(raiz.atividadeFinal(), Set.of()) instanceof ResolucaoDeEtapa.Encerrado(FinalDoFluxo f)) {
            fim = Optional.of(f);
        } else {
            marcas.add(MarcaDaPosicao.ETAPA_NAO_MAPEADA);
            naoMapeados.add(Objects.requireNonNullElse(raiz.atividadeFinal(), ""));
        }
        return new PosicaoAtual(EstadoDaPosicao.ENCERRADO, Optional.of(modelo.chave()), Optional.of(modelo.rotulo()),
                Optional.empty(), List.of(), Optional.empty(), fim, Optional.of(raiz.fim()), Optional.empty(), marcas,
                List.copyOf(naoMapeados), diagnostico);
    }

    /** Montagem da instância ativa (estado de trabalho de uma chamada). */
    private static final class Ativa {

        private final ModeloDeFluxo modelo;
        private final LeituraDoFluxo leitura;
        private final LeituraDoFluxo.InstanciaRaiz raiz;
        private final Set<MarcaDaPosicao> marcas;
        private final Set<String> naoMapeados;
        private final Set<String> percorridos;
        private final Map<String, LocalDateTime> inicioDoCiclo = new HashMap<>();
        private final List<ElementoDaPosicao> pontos = new ArrayList<>();
        private LocalDateTime suporteDesde;
        private boolean suporteAtivo;

        Ativa(ModeloDeFluxo modelo, LeituraDoFluxo leitura, LeituraDoFluxo.InstanciaRaiz raiz, Set<MarcaDaPosicao> marcas,
                Set<String> naoMapeados) {
            this.modelo = modelo;
            this.leitura = leitura;
            this.raiz = raiz;
            this.marcas = marcas;
            this.naoMapeados = naoMapeados;
            this.percorridos = leitura.percorridas().stream().map(LeituraDoFluxo.Percorrida::idTecnico)
                    .collect(Collectors.toUnmodifiableSet());
        }

        PosicaoAtual montar(Optional<String> diagnostico) {
            for (LeituraDoFluxo.Execucao execucao : leitura.execucoes()) {
                if (execucao.idTecnico().endsWith(CORPO_MULTI_INSTANCIA) && execucao.desde() != null) {
                    inicioDoCiclo.merge(base(execucao.idTecnico()), execucao.desde(),
                            (a, b) -> a.isAfter(b) ? a : b);
                }
            }
            tarefasDaRaiz();
            filhas();
            execucoes();

            Optional<ElementoDaPosicao> principal;
            List<ElementoDaPosicao> emParalelo;
            List<ElementoDaPosicao> ordenados = pontos.stream().sorted(ORDEM_DOS_PONTOS).toList();
            if (suporteAtivo) {
                marcas.add(MarcaDaPosicao.EM_SUPORTE);
            }
            if (suporteAtivo && pontos.stream().noneMatch(ElementoDaPosicao::mapeado)) {
                principal = ultimaEtapaReal();
                emParalelo = ordenados;
            } else {
                principal = ordenados.stream().findFirst();
                emParalelo = ordenados.isEmpty() ? List.of() : ordenados.subList(1, ordenados.size());
            }
            if (principal.isEmpty()) {
                marcas.add(MarcaDaPosicao.ETAPA_NAO_MAPEADA);
            }
            return new PosicaoAtual(EstadoDaPosicao.ATIVO, Optional.of(modelo.chave()), Optional.of(modelo.rotulo()),
                    principal, emParalelo, complementacao(), Optional.empty(), Optional.empty(),
                    Optional.ofNullable(suporteDesde), marcas, List.copyOf(naoMapeados), diagnostico);
        }

        private void tarefasDaRaiz() {
            Map<String, List<LeituraDoFluxo.Tarefa>> porId = new LinkedHashMap<>();
            leitura.tarefas().stream()
                    .filter(t -> raiz.id().equals(t.instancia()))
                    .sorted(Comparator.comparing(LeituraDoFluxo.Tarefa::criadaEm, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(t -> porId.computeIfAbsent(t.idTecnico(), k -> new ArrayList<>()).add(t));
            porId.forEach((idTecnico, tarefas) -> {
                LocalDateTime primeira = tarefas.getFirst().criadaEm();
                switch (modelo.resolver(idTecnico, percorridos)) {
                    case ResolucaoDeEtapa.Ignorado(IdIgnorado ig) -> {
                        if (ig.motivo() == IdIgnorado.Motivo.SUPORTE) {
                            suporte(primeira);
                        }
                    }
                    case ResolucaoDeEtapa.NaEtapa(Etapa etapa) -> pontos.add(etapa.multiInstancia()
                            ? multiInstancia(etapa, idTecnico, tarefas)
                            : new ElementoDaPosicao(Optional.of(etapa), idTecnico, primeira, true, tarefas, Optional.empty(),
                                    Optional.empty()));
                    default -> naoMapeado(idTecnico, primeira, tarefas);
                }
            });
        }

        private ElementoDaPosicao multiInstancia(Etapa etapa, String idTecnico, List<LeituraDoFluxo.Tarefa> ativas) {
            LocalDateTime ciclo = inicioDoCiclo.get(idTecnico);
            List<LeituraDoFluxo.Percorrida> doCiclo = ciclo == null ? List.of() : leitura.percorridas().stream()
                    .filter(p -> p.tarefa() && idTecnico.equals(p.idTecnico()) && p.inicio() != null
                            && !p.inicio().isBefore(ciclo))
                    .sorted(Comparator.comparing(LeituraDoFluxo.Percorrida::fim,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            Optional<Integer> concluidos = ciclo == null ? Optional.empty() : Optional.of(doCiclo.size());
            LocalDateTime desde = ciclo != null ? ciclo : ativas.isEmpty() ? null : ativas.getFirst().criadaEm();
            return new ElementoDaPosicao(Optional.of(etapa), idTecnico, desde, true, ativas,
                    Optional.of(new ElementoDaPosicao.MultiInstancia(concluidos, ativas.size())), Optional.empty())
                    .comConcluidas(doCiclo);
        }

        private void filhas() {
            for (LeituraDoFluxo.Filha filha : leitura.filhas()) {
                List<LeituraDoFluxo.Tarefa> tarefas = leitura.tarefas().stream()
                        .filter(t -> filha.id().equals(t.instancia())).toList();
                if (modelo.resolverProcessoFilho(filha.chave()) instanceof ResolucaoDeEtapa.NaEtapa(Etapa etapa)) {
                    pontos.add(new ElementoDaPosicao(Optional.of(etapa), filha.chave(), filha.inicio(), false, tarefas,
                            Optional.empty(), Optional.of(filha.chave())));
                } else {
                    naoMapeado(filha.chave(), filha.inicio(), tarefas);
                }
            }
        }

        private void execucoes() {
            Set<String> comTarefa = leitura.tarefas().stream()
                    .filter(t -> raiz.id().equals(t.instancia()))
                    .map(LeituraDoFluxo.Tarefa::idTecnico).collect(Collectors.toSet());
            for (LeituraDoFluxo.Execucao execucao : leitura.execucoes()) {
                String idTecnico = execucao.idTecnico();
                if (idTecnico.endsWith(CORPO_MULTI_INSTANCIA)) {
                    corpoSemTarefa(base(idTecnico), comTarefa);
                    continue;
                }
                if (comTarefa.contains(idTecnico)) {
                    continue;
                }
                switch (modelo.resolver(idTecnico, percorridos)) {
                    case ResolucaoDeEtapa.Ignorado(IdIgnorado ig) -> {
                        if (ig.motivo() == IdIgnorado.Motivo.SUPORTE) {
                            suporte(execucao.desde());
                        }
                    }
                    case ResolucaoDeEtapa.NaEtapa(Etapa etapa) -> {
                        if (representada(etapa)) {
                            continue; // ex.: a chamada (callActivity) de uma emissão já mostrada pela filha
                        }
                        if (etapa.tipo() == TipoDeEtapa.ESPERA) {
                            if (execucao.assinadaEm() == null) {
                                naoMapeado(idTecnico, execucao.desde(), List.of());
                            } else {
                                pontos.add(new ElementoDaPosicao(Optional.of(etapa), idTecnico, execucao.assinadaEm(), false,
                                        List.of(), Optional.empty(), Optional.empty()));
                            }
                        } else {
                            pontos.add(new ElementoDaPosicao(Optional.of(etapa), idTecnico, execucao.desde(), false, List.of(),
                                    Optional.empty(), Optional.empty()));
                        }
                    }
                    case ResolucaoDeEtapa.Encerrado ignorado -> {
                        // Execução passando por um evento de fim: transitório, a instância encerra em seguida.
                    }
                    default -> naoMapeado(idTecnico, execucao.desde(), List.of());
                }
            }
        }

        /** Corpo da multi-instância aberto sem nenhuma tarefa ativa (transição entre ciclos): a etapa, sem inventar o "y". */
        private void corpoSemTarefa(String idTecnico, Set<String> comTarefa) {
            if (comTarefa.contains(idTecnico)) {
                return;
            }
            if (modelo.resolver(idTecnico, percorridos) instanceof ResolucaoDeEtapa.NaEtapa(Etapa etapa)
                    && !representada(etapa)) {
                pontos.add(multiInstancia(etapa, idTecnico, List.of()));
            }
        }

        private boolean representada(Etapa etapa) {
            return pontos.stream().anyMatch(p -> p.etapa().filter(etapa::equals).isPresent());
        }

        private void suporte(LocalDateTime desde) {
            suporteAtivo = true;
            if (desde != null && (suporteDesde == null || desde.isBefore(suporteDesde))) {
                suporteDesde = desde;
            }
        }

        private void naoMapeado(String idTecnico, LocalDateTime desde, List<LeituraDoFluxo.Tarefa> tarefas) {
            marcas.add(MarcaDaPosicao.ETAPA_NAO_MAPEADA);
            naoMapeados.add(idTecnico);
            pontos.add(new ElementoDaPosicao(Optional.empty(), idTecnico, desde, false, tarefas, Optional.empty(),
                    Optional.empty()));
        }

        /** A atividade encerrada mais recente que está no catálogo e não é ignorada; sem "desde" (não se sabe). */
        private Optional<ElementoDaPosicao> ultimaEtapaReal() {
            return leitura.percorridas().stream()
                    .sorted(Comparator.comparing(LeituraDoFluxo.Percorrida::fim,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(p -> modelo.resolver(p.idTecnico(), percorridos) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e)
                            ? new ElementoDaPosicao(Optional.of(e), p.idTecnico(), null, false, List.of(), Optional.empty(),
                                    Optional.empty())
                            : null)
                    .filter(Objects::nonNull)
                    .findFirst();
        }

        private Optional<PosicaoAtual.ComplementacaoEmAndamento> complementacao() {
            List<LeituraDoFluxo.Complementacao> ativas = leitura.complementacoes();
            if (ativas.isEmpty()) {
                return Optional.empty();
            }
            LocalDateTime desde = ativas.stream().map(LeituraDoFluxo.Complementacao::inicio).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            return Optional.of(new PosicaoAtual.ComplementacaoEmAndamento(ativas.size(), desde));
        }
    }

    private static String base(String idDoCorpo) {
        return idDoCorpo.substring(0, idDoCorpo.length() - CORPO_MULTI_INSTANCIA.length());
    }

    /** Ramo principal pela ordem, depois correção pela ordem, depois os não mapeados. */
    private static int prioridade(ElementoDaPosicao ponto) {
        return ponto.etapa()
                .map(e -> (e.ramo() == Ramo.PRINCIPAL ? 0 : 1_000) + e.ordem())
                .orElse(1_000_000);
    }
}
