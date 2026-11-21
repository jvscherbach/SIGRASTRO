package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import br.gov.mt.sema.rastro.tramitacao.domain.AtividadeBruta.MotivoDaTarefa;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido.ClassificacaoDaTaxa;

/**
 * Reconstrói o caminho percorrido em etapas simplificadas (S8, bloco 3; regras em docs/15, "Reconstrução do caminho").
 * Função pura: entra o {@link CaminhoBruto} (bloco 2), o modelo do catálogo e a identificação dos responsáveis (S7, bloco 4);
 * sai o {@link CaminhoReconstruido}. Sem banco, sem Spring.
 *
 * <ol>
 *   <li><b>Percorrida</b> = atividade com fim e, se for tarefa, concluída ({@code completed}); tarefa encerrada sem ação não
 *       vira passagem. A atividade aberta vira a passagem atual.</li>
 *   <li><b>Ruído:</b> ids ignorados do catálogo saem (contados); id fora do catálogo que não é tarefa sai (vai para o
 *       diagnóstico); tarefa fora do catálogo vira passagem "Etapa não mapeada".</li>
 *   <li><b>Repetições consecutivas</b> da mesma etapa (scripts repetidos por erro, os vários ids de uma etapa automática)
 *       viram uma passagem; duas tarefas humanas seguidas da mesma etapa continuam duas passagens.</li>
 *   <li><b>Multi-instância:</b> uma passagem por ciclo, delimitado pelo corpo {@code <tarefa>#multiInstanceBody}; sem o
 *       corpo, as tarefas seguidas da etapa formam o ciclo. Ciclo encerrado sem nenhuma tarefa concluída não é
 *       percorrido.</li>
 *   <li><b>Emissão:</b> as {@code callActivity} seguidas e as filhas viram uma passagem "Emissão do ato" com os atos.</li>
 *   <li><b>Ciclo</b> = n-ésima passagem pela etapa; <b>motivo da volta</b> por {@link MotivosDaVolta}.</li>
 *   <li><b>Taxável × isento</b> pela rota; o {@code statusConferencia} da época só confirma.</li>
 * </ol>
 */
public final class ReconstruirCaminho {

    private static final String CORPO = "#multiInstanceBody";
    private static final Pattern CPF = Pattern.compile("\\d{11}");
    private static final String PAPEL_SIGNATARIO = "Signatário";

    private ReconstruirCaminho() {
    }

    /** Os {@code ASSIGNEE_} do caminho que valem consulta ao SIGA (CPF de 11 dígitos), sem repetição. */
    public static List<IdentificadorDoResponsavel> paraConsultar(CaminhoBruto caminho) {
        Set<IdentificadorDoResponsavel> ids = new LinkedHashSet<>();
        for (AtividadeBruta a : caminho.atividades()) {
            a.responsavel().ifPresent(ids::add);
            a.tarefa().flatMap(AtividadeBruta.TarefaBruta::responsavel).ifPresent(ids::add);
        }
        return ids.stream().filter(id -> CPF.matcher(id.valorProtegido()).matches()).toList();
    }

    public static CaminhoReconstruido reconstruir(CaminhoBruto caminho, ModeloDeFluxo modelo,
            Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificados) {
        Objects.requireNonNull(modelo, "modelo");
        if (caminho == null || caminho.raiz().isEmpty()) {
            return CaminhoReconstruido.vazio();
        }
        return new Execucao(caminho, modelo, Objects.requireNonNullElse(identificados, Map.of())).executar();
    }

    // --- um "item" é uma passagem em construção

    private static final class Item {
        final Etapa etapa;
        final AtividadeBruta primeira;
        final List<AtividadeBruta> partes = new ArrayList<>();
        final List<AtividadeBruta> tarefas = new ArrayList<>();
        final List<InstanciaDoHistorico> filhas = new ArrayList<>();
        final List<AtividadeBruta> chamadasSemFilha = new ArrayList<>();
        AtividadeBruta corpo;
        int repeticoes;

        Item(Etapa etapa, AtividadeBruta primeira) {
            this.etapa = etapa;
            this.primeira = primeira;
        }

        boolean multi() {
            return etapa != null && etapa.multiInstancia();
        }

        boolean emissao() {
            return etapa != null && etapa.tipo() == TipoDeEtapa.EMISSAO;
        }

        LocalDateTime entrada() {
            if (corpo != null) {
                return corpo.inicio();
            }
            return datas().map(d -> d[0]).min(Comparator.naturalOrder()).orElse(primeira.inicio());
        }

        boolean aberta() {
            if (corpo != null && corpo.aberta()) {
                return true;
            }
            return partes.stream().anyMatch(AtividadeBruta::aberta) || tarefas.stream().anyMatch(AtividadeBruta::aberta)
                    || filhas.stream().anyMatch(f -> !f.encerrada());
        }

        Optional<LocalDateTime> saida() {
            if (aberta()) {
                return Optional.empty();
            }
            if (corpo != null) {
                return Optional.ofNullable(corpo.fim());
            }
            return datas().map(d -> d[1]).filter(Objects::nonNull).max(Comparator.naturalOrder());
        }

        private java.util.stream.Stream<LocalDateTime[]> datas() {
            List<LocalDateTime[]> d = new ArrayList<>();
            partes.forEach(a -> d.add(new LocalDateTime[] {a.inicio(), a.fim()}));
            tarefas.forEach(a -> d.add(new LocalDateTime[] {a.inicio(), a.fim()}));
            filhas.forEach(f -> d.add(new LocalDateTime[] {f.inicio(), f.fim()}));
            return d.stream().filter(x -> x[0] != null);
        }

        boolean temTarefa() {
            return !tarefas.isEmpty();
        }

        /** Ciclo encerrado sem nenhuma tarefa concluída: não foi percorrido. */
        boolean cicloSemConclusao() {
            return multi() && !aberta() && tarefas.stream().noneMatch(ReconstruirCaminho::concluida);
        }
    }

    private static final class Execucao {
        private final CaminhoBruto caminho;
        private final ModeloDeFluxo modelo;
        private final Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificados;
        private final Map<String, InstanciaDoHistorico> filhasPorId = new HashMap<>();
        private final Set<String> filhasUsadas = new HashSet<>();
        private final Set<String> percorridos = new HashSet<>();
        private final List<Item> itens = new ArrayList<>();
        private final Set<String> naoMapeados = new LinkedHashSet<>();
        private int ignoradas;

        Execucao(CaminhoBruto caminho, ModeloDeFluxo modelo,
                Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificados) {
            this.caminho = caminho;
            this.modelo = modelo;
            this.identificados = identificados;
            caminho.instancias().stream().filter(i -> i.origem() == OrigemDaInstancia.FILHA)
                    .forEach(f -> filhasPorId.put(f.id(), f));
        }

        CaminhoReconstruido executar() {
            InstanciaDoHistorico raiz = caminho.raiz().orElseThrow();
            for (AtividadeBruta a : caminho.atividadesDe(raiz.id())) {
                processar(a);
                if (!a.aberta()) {
                    percorridos.add(a.idTecnico());
                }
            }
            filhasSemChamada();
            itens.removeIf(Item::cicloSemConclusao);
            List<Item> ordenados = itens.stream().sorted(Comparator.comparing(Item::entrada)).toList();

            List<CaminhoReconstruido.Complementacao> complementacoes = caminho.instancias().stream()
                    .filter(i -> i.origem() == OrigemDaInstancia.COMPLEMENTACAO && i.inicio() != null)
                    .sorted(Comparator.comparing(InstanciaDoHistorico::inicio))
                    .map(i -> new CaminhoReconstruido.Complementacao(i.inicio(), Optional.ofNullable(i.fim())))
                    .toList();

            List<PassagemPelaEtapa> passagens = passagens(ordenados, complementacoes);
            ClassificacaoDaTaxa principal = taxaPrincipal(passagens);
            Optional<ClassificacaoDaTaxa> correcao = taxaDaCorrecao(passagens);
            Set<CaminhoReconstruido.Diagnostico> diagnosticos = new HashSet<>();
            if (rotaDivergeDoStatus(principal)) {
                diagnosticos.add(CaminhoReconstruido.Diagnostico.ROTA_DIVERGE_DO_STATUS);
            }
            return new CaminhoReconstruido(passagens, complementacoes, principal, correcao, naoMapeados, ignoradas,
                    diagnosticos, caminho.truncado(), caminho.instanciasRaiz());
        }

        // --- leitura das atividades da raiz

        private void processar(AtividadeBruta a) {
            if (a.idTecnico().endsWith(CORPO)) {
                String base = a.idTecnico().substring(0, a.idTecnico().length() - CORPO.length());
                if (modelo.resolver(base, percorridos) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e) && e.multiInstancia()) {
                    Item ciclo = new Item(e, a);
                    ciclo.corpo = a;
                    itens.add(ciclo);
                }
                return;
            }
            switch (modelo.resolver(a.idTecnico(), percorridos)) {
                case ResolucaoDeEtapa.Ignorado ignorado -> ignoradas++;
                case ResolucaoDeEtapa.Encerrado fim -> {
                    // o final (bola preta) é do bloco 4
                }
                case ResolucaoDeEtapa.NaoSuportado nao -> {
                    // impossível com um modelo em mãos
                }
                case ResolucaoDeEtapa.NaoMapeado nao -> {
                    naoMapeados.add(a.idTecnico());
                    if (ehTarefa(a) && percorridaOuAberta(a)) {
                        Item item = new Item(null, a);
                        item.tarefas.add(a);
                        itens.add(item);
                    }
                }
                case ResolucaoDeEtapa.NaEtapa(Etapa e) -> {
                    if (e.multiInstancia() && ehTarefa(a)) {
                        noCiclo(e, a);
                    } else if (e.tipo() == TipoDeEtapa.EMISSAO) {
                        naEmissao(e, a);
                    } else {
                        simples(e, a);
                    }
                }
            }
        }

        private void noCiclo(Etapa e, AtividadeBruta a) {
            Item ciclo = null;
            for (int i = itens.size() - 1; i >= 0; i--) {
                Item it = itens.get(i);
                if (it.etapa == e && it.multi()) {
                    ciclo = it;
                    break;
                }
            }
            boolean cabe = ciclo != null && (ciclo.corpo != null ? dentro(ciclo.corpo, a) : ciclo == ultimo());
            if (!cabe) {
                ciclo = new Item(e, a);
                itens.add(ciclo);
            }
            ciclo.tarefas.add(a);
        }

        private static boolean dentro(AtividadeBruta corpo, AtividadeBruta tarefa) {
            return !tarefa.inicio().isBefore(corpo.inicio()) && (corpo.fim() == null || !tarefa.inicio().isAfter(corpo.fim()));
        }

        private void naEmissao(Etapa e, AtividadeBruta a) {
            Item item = ultimo();
            if (item == null || item.etapa != e) {
                item = new Item(e, a);
                itens.add(item);
            } else {
                item.repeticoes++;
            }
            InstanciaDoHistorico filha = a.chamada() == null ? null : filhasPorId.get(a.chamada());
            if (filha != null) {
                filhasUsadas.add(filha.id());
                item.filhas.add(filha);
            } else if (a.chamada() != null || "callActivity".equals(a.tipo())) {
                item.chamadasSemFilha.add(a);
            }
            item.partes.add(a);
        }

        private void simples(Etapa e, AtividadeBruta a) {
            if (ehTarefa(a) && !percorridaOuAberta(a)) {
                return;
            }
            Item item = ultimo();
            boolean junta = item != null && item.etapa == e && !item.multi() && !(item.temTarefa() && ehTarefa(a));
            if (junta) {
                item.repeticoes++;
            } else {
                item = new Item(e, a);
                itens.add(item);
            }
            if (ehTarefa(a)) {
                item.tarefas.add(a);
            } else {
                item.partes.add(a);
            }
        }

        /** Filha sem a chamada na raiz (ex.: raiz truncada): vira emissão própria, na posição pelo início. */
        private void filhasSemChamada() {
            filhasPorId.values().stream().filter(f -> !filhasUsadas.contains(f.id()) && f.inicio() != null)
                    .sorted(Comparator.comparing(InstanciaDoHistorico::inicio)).forEach(f -> {
                        if (modelo.resolverProcessoFilho(f.chave()) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e)) {
                            AtividadeBruta marco = new AtividadeBruta(f.id(), OrigemDaInstancia.FILHA, f.id(), null, null,
                                    f.chave(), null, "callActivity", f.inicio(), f.fim(), Optional.empty(), f.id(),
                                    Optional.empty());
                            Item item = new Item(e, marco);
                            item.filhas.add(f);
                            itens.add(item);
                        }
                    });
        }

        private Item ultimo() {
            return itens.isEmpty() ? null : itens.getLast();
        }

        // --- passagens

        private List<PassagemPelaEtapa> passagens(List<Item> ordenados,
                List<CaminhoReconstruido.Complementacao> complementacoes) {
            List<LocalDateTime[]> janelas = new ArrayList<>();
            ordenados.forEach(i -> janelas.add(new LocalDateTime[] {i.entrada(), i.saida().orElse(LocalDateTime.MAX)}));
            Map<String, Integer> ciclos = new HashMap<>();
            List<PassagemPelaEtapa> passagens = new ArrayList<>();
            for (int n = 0; n < ordenados.size(); n++) {
                Item i = ordenados.get(n);
                String chave = i.etapa != null ? i.etapa.id() : "?" + i.primeira.idTecnico();
                int ciclo = ciclos.merge(chave, 1, Integer::sum);
                boolean paralelo = false;
                for (int m = 0; m < janelas.size() && !paralelo; m++) {
                    paralelo = m != n && sobrepoe(janelas.get(n), janelas.get(m));
                }
                for (CaminhoReconstruido.Complementacao c : complementacoes) {
                    paralelo |= sobrepoe(janelas.get(n),
                            new LocalDateTime[] {c.entrada(), c.saida().orElse(LocalDateTime.MAX)});
                }
                passagens.add(new PassagemPelaEtapa(Optional.ofNullable(i.etapa), i.primeira.idTecnico(), ciclo,
                        i.etapa != null ? i.etapa.ramo() : Ramo.PRINCIPAL, i.entrada(), i.saida(), responsaveis(i),
                        i.multi(), atos(i), Optional.empty(), paralelo, i.repeticoes + 1,
                        i.etapa != null && MotivosDaVolta.VERIFICACAO.equals(i.etapa.id())));
            }
            List<PassagemPelaEtapa> comMotivo = new ArrayList<>();
            for (int n = 0; n < passagens.size(); n++) {
                PassagemPelaEtapa p = passagens.get(n);
                comMotivo.add(p.comMotivo(MotivosDaVolta.motivo(p, n == 0 ? null : passagens.get(n - 1),
                        caminho.variaveisDaEpoca())));
            }
            return comMotivo;
        }

        private static boolean sobrepoe(LocalDateTime[] a, LocalDateTime[] b) {
            return a[0].isBefore(b[1]) && b[0].isBefore(a[1]);
        }

        private List<AtoDaEmissao> atos(Item i) {
            if (!i.emissao()) {
                return List.of();
            }
            List<AtoDaEmissao> atos = new ArrayList<>();
            for (InstanciaDoHistorico f : i.filhas) {
                AtoDaEmissao.Estado estado = !f.encerrada() ? AtoDaEmissao.Estado.EM_ANDAMENTO
                        : "COMPLETED".equals(f.estado()) ? AtoDaEmissao.Estado.EMITIDO : AtoDaEmissao.Estado.NAO_CONCLUIDO;
                atos.add(new AtoDaEmissao(AtoDaEmissao.Tipo.doProcesso(f.chave()), f.inicio(), f.fim(), estado));
            }
            for (AtividadeBruta c : i.chamadasSemFilha) {
                atos.add(new AtoDaEmissao(AtoDaEmissao.Tipo.daChamada(c.idTecnico()), c.inicio(), c.fim(),
                        c.aberta() ? AtoDaEmissao.Estado.EM_ANDAMENTO : AtoDaEmissao.Estado.INDETERMINADO));
            }
            atos.sort(Comparator.comparing(AtoDaEmissao::inicio, Comparator.nullsLast(Comparator.naturalOrder())));
            return atos;
        }

        // --- com quem esteve

        private List<ResponsavelDaPassagem> responsaveis(Item i) {
            PapelDoResponsavel papel = i.etapa != null ? i.etapa.responsavel() : null;
            String rotuloDoPapel = papel != null ? RegraDoResponsavel.rotulo(papel) : "Responsável";
            List<ResponsavelDaPassagem> lista = new ArrayList<>();
            if (i.emissao()) {
                for (InstanciaDoHistorico f : i.filhas) {
                    caminho.atividadesDe(f.id()).stream().filter(ReconstruirCaminho::ehTarefa)
                            .forEach(t -> lista.add(daTarefa(t, PAPEL_SIGNATARIO, papel)));
                }
                if (!lista.isEmpty()) {
                    return lista;
                }
            }
            i.tarefas.forEach(t -> lista.add(daTarefa(t, rotuloDoPapel, papel)));
            if (lista.isEmpty()) {
                lista.add(semTarefa(i, papel));
            }
            return lista;
        }

        private ResponsavelDaPassagem daTarefa(AtividadeBruta t, String rotuloDoPapel, PapelDoResponsavel papel) {
            ResponsavelDaPassagem.Estado estado = t.aberta() ? ResponsavelDaPassagem.Estado.PENDENTE
                    : concluida(t) ? ResponsavelDaPassagem.Estado.CONCLUIU
                            : ResponsavelDaPassagem.Estado.ENCERRADA_SEM_ACAO;
            Optional<IdentificadorDoResponsavel> quem = t.tarefa().flatMap(AtividadeBruta.TarefaBruta::responsavel)
                    .or(t::responsavel);
            IdentificacaoDoResponsavel id = quem.map(identificados::get).orElse(null);
            if (id != null && id.interessado()) {
                return new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.INTERESSADO, ResponsavelDaEtapa.INTERESSADO,
                        Optional.empty(), t.inicio(), t.fim(), estado);
            }
            if (id != null && id.nomeDoServidor().isPresent()) {
                return new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.SERVIDOR, rotuloDoPapel, id.nomeDoServidor(),
                        t.inicio(), t.fim(), estado);
            }
            if (quem.isPresent()) {
                return new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.NAO_IDENTIFICADO,
                        ResponsavelDaEtapa.NAO_IDENTIFICADO, Optional.empty(), t.inicio(), t.fim(), estado);
            }
            if (papel == PapelDoResponsavel.INTERESSADO) {
                return new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.INTERESSADO, ResponsavelDaEtapa.INTERESSADO,
                        Optional.empty(), t.inicio(), t.fim(), estado);
            }
            return new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.EQUIPE, ResponsavelDaEtapa.EQUIPE, Optional.empty(),
                    t.inicio(), t.fim(), estado);
        }

        private static ResponsavelDaPassagem semTarefa(Item i, PapelDoResponsavel papel) {
            ResponsavelDaPassagem.Estado estado = i.aberta() ? ResponsavelDaPassagem.Estado.PENDENTE
                    : ResponsavelDaPassagem.Estado.CONCLUIU;
            LocalDateTime fim = i.saida().orElse(null);
            if (papel == PapelDoResponsavel.INTERESSADO) {
                return new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.INTERESSADO, ResponsavelDaEtapa.INTERESSADO,
                        Optional.empty(), i.entrada(), fim, estado);
            }
            boolean humana = i.etapa != null
                    && (i.etapa.tipo() == TipoDeEtapa.TAREFA_HUMANA || i.etapa.tipo() == TipoDeEtapa.EMISSAO);
            if (papel == null || papel == PapelDoResponsavel.SISTEMA || !humana) {
                return new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.SISTEMA, ResponsavelDaEtapa.SISTEMA,
                        Optional.empty(), i.entrada(), fim, estado);
            }
            return new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.EQUIPE, ResponsavelDaEtapa.EQUIPE, Optional.empty(),
                    i.entrada(), fim, estado);
        }

        // --- taxável × isento

        private static ClassificacaoDaTaxa taxaPrincipal(List<PassagemPelaEtapa> passagens) {
            boolean espera = passagens.stream().anyMatch(p -> p.ramo() == Ramo.PRINCIPAL
                    && p.etapa().map(e -> e.tipo() == TipoDeEtapa.ESPERA).orElse(false));
            if (espera) {
                return ClassificacaoDaTaxa.TAXAVEL;
            }
            boolean isencao = passagens.stream()
                    .anyMatch(p -> p.idDaEtapa().map(MotivosDaVolta.ISENCAO::equals).orElse(false));
            return isencao ? ClassificacaoDaTaxa.ISENTA : ClassificacaoDaTaxa.INDETERMINADA;
        }

        private static Optional<ClassificacaoDaTaxa> taxaDaCorrecao(List<PassagemPelaEtapa> passagens) {
            List<PassagemPelaEtapa> correcao = passagens.stream().filter(p -> p.ramo() == Ramo.CORRECAO).toList();
            if (correcao.isEmpty()) {
                return Optional.empty();
            }
            boolean espera = correcao.stream().anyMatch(p -> p.etapa().map(e -> e.tipo() == TipoDeEtapa.ESPERA)
                    .orElse(false));
            return Optional.of(espera ? ClassificacaoDaTaxa.TAXAVEL : ClassificacaoDaTaxa.SEM_NOVA_TAXA);
        }

        /** Último {@code statusConferencia} da época que é {@code isento} ou {@code taxavel}, contra a rota. */
        private boolean rotaDivergeDoStatus(ClassificacaoDaTaxa rota) {
            if (rota != ClassificacaoDaTaxa.TAXAVEL && rota != ClassificacaoDaTaxa.ISENTA) {
                return false;
            }
            String ultimo = null;
            for (VariavelBruta v : caminho.variaveisDaEpoca()) {
                if (v.nome().equals(MotivosDaVolta.STATUS_CONFERENCIA)) {
                    String valor = v.valor().map(String::strip).orElse("");
                    if (valor.equalsIgnoreCase("isento") || valor.equalsIgnoreCase("taxavel")) {
                        ultimo = valor;
                    }
                }
            }
            if (ultimo == null) {
                return false;
            }
            return rota == ClassificacaoDaTaxa.TAXAVEL ? ultimo.equalsIgnoreCase("isento")
                    : ultimo.equalsIgnoreCase("taxavel");
        }
    }

    // --- auxiliares

    static boolean ehTarefa(AtividadeBruta a) {
        return a.tarefa().isPresent() || "userTask".equals(a.tipo());
    }

    static boolean concluida(AtividadeBruta a) {
        return !a.aberta() && a.tarefa().map(t -> t.motivo() == MotivoDaTarefa.CONCLUIDA).orElse(true);
    }

    /** Aberta, ou encerrada e (se tiver tarefa) concluída. */
    static boolean percorridaOuAberta(AtividadeBruta a) {
        return a.aberta() || concluida(a);
    }
}
