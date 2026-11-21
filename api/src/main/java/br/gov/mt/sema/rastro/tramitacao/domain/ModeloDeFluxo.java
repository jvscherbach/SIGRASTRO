package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Modelo de fluxo de uma chave de definição do Camunda ({@code PROC_DEF_KEY_}), com as etapas simplificadas, os finais e os
 * ids ignorados. Só existe válido: {@link #criar} recusa o conteúdo com problema ({@link ValidadorDeModelo}).
 */
public final class ModeloDeFluxo {

    private final String chave;
    private final String rotulo;
    private final List<String> instancias;
    private final List<Etapa> etapas;
    private final List<FinalDoFluxo> finais;
    private final List<IdIgnorado> ignorados;
    private final Map<String, ResolucaoDeEtapa> porIdTecnico = new HashMap<>();
    private final Map<String, Etapa> porProcessoFilho = new HashMap<>();

    private ModeloDeFluxo(String chave, String rotulo, List<String> instancias, List<Etapa> etapas,
            List<FinalDoFluxo> finais, List<IdIgnorado> ignorados) {
        this.chave = chave;
        this.rotulo = rotulo;
        this.instancias = List.copyOf(instancias);
        this.etapas = etapas.stream()
                .sorted(Comparator.comparing(Etapa::ramo).thenComparingInt(Etapa::ordem))
                .toList();
        this.finais = List.copyOf(finais);
        this.ignorados = List.copyOf(ignorados);
        for (Etapa etapa : this.etapas) {
            etapa.idsTecnicos().forEach(id -> porIdTecnico.put(id, new ResolucaoDeEtapa.NaEtapa(etapa)));
            etapa.processosFilhos().forEach(filho -> porProcessoFilho.put(filho, etapa));
        }
        this.finais.forEach(fim -> porIdTecnico.put(fim.idTecnico(), new ResolucaoDeEtapa.Encerrado(fim)));
        this.ignorados.forEach(ig -> porIdTecnico.put(ig.idTecnico(), new ResolucaoDeEtapa.Ignorado(ig)));
    }

    /**
     * @param origem      de onde veio o conteúdo (nome do arquivo), para a mensagem de erro
     * @param instancias  valores de {@code TIPO_PROCESSO.INSTANCIA_REQUERIMENTO} que usam este modelo
     * @throws ModeloDeFluxoInvalido com todos os problemas encontrados
     */
    public static ModeloDeFluxo criar(String origem, String chave, String rotulo, List<String> instancias,
            List<Etapa> etapas, List<FinalDoFluxo> finais, List<IdIgnorado> ignorados) {
        List<String> problemas = ValidadorDeModelo.problemas(chave, rotulo, etapas, finais, ignorados);
        if (!problemas.isEmpty()) {
            throw new ModeloDeFluxoInvalido(origem, problemas);
        }
        return new ModeloDeFluxo(chave, rotulo, Objects.requireNonNullElse(instancias, List.of()), etapas, finais,
                Objects.requireNonNullElse(ignorados, List.of()));
    }

    /**
     * Resolve um id de atividade ({@code ACT_ID_}, {@code TASK_DEF_KEY_} ou {@code END_ACT_ID_}).
     *
     * @param percorridos ids técnicos já percorridos pela instância, para as etapas com condição (ex.: isenção confirmada
     *                    só sem passagem pelo gateway de pagamento). Etapa com condição não atendida vira
     *                    {@link ResolucaoDeEtapa.NaoMapeado}: o Rastro diz que não sabe, em vez de adivinhar
     */
    public ResolucaoDeEtapa resolver(String idTecnico, Set<String> percorridos) {
        if (idTecnico == null || idTecnico.isBlank()) {
            return new ResolucaoDeEtapa.NaoMapeado("");
        }
        ResolucaoDeEtapa resolucao = porIdTecnico.get(idTecnico);
        if (resolucao == null) {
            return new ResolucaoDeEtapa.NaoMapeado(idTecnico);
        }
        if (resolucao instanceof ResolucaoDeEtapa.NaEtapa(Etapa etapa)
                && !etapa.aceitaPercurso(Objects.requireNonNullElse(percorridos, Set.of()))) {
            return new ResolucaoDeEtapa.NaoMapeado(idTecnico);
        }
        return resolucao;
    }

    /** Resolve uma instância filha pela chave de definição dela (ex.: {@code procSurhEmissaoPortaria}). */
    public ResolucaoDeEtapa resolverProcessoFilho(String chaveDoFilho) {
        Etapa etapa = chaveDoFilho == null ? null : porProcessoFilho.get(chaveDoFilho);
        return etapa == null
                ? new ResolucaoDeEtapa.NaoMapeado(Objects.requireNonNullElse(chaveDoFilho, ""))
                : new ResolucaoDeEtapa.NaEtapa(etapa);
    }

    public Optional<Etapa> etapa(String id) {
        return etapas.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /** Ids das tarefas de suporte técnico do modelo (bloco 3: não são a posição). */
    public Set<String> idsDeSuporte() {
        return ignorados.stream()
                .filter(ig -> ig.motivo() == IdIgnorado.Motivo.SUPORTE)
                .map(IdIgnorado::idTecnico)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public String chave() {
        return chave;
    }

    public String rotulo() {
        return rotulo;
    }

    public List<String> instancias() {
        return instancias;
    }

    /** Etapas ordenadas por ramo (principal antes de correção) e ordem. */
    public List<Etapa> etapas() {
        return etapas;
    }

    public List<FinalDoFluxo> finais() {
        return finais;
    }

    public List<IdIgnorado> ignorados() {
        return ignorados;
    }
}
