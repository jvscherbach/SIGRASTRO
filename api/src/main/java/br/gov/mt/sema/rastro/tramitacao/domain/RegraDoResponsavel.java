package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Com quem está cada ponto da posição (S7, bloco 4; docs/15, "Responsável"). Função pura, sem banco: recebe o que o SIGA
 * disse de cada {@code ASSIGNEE_} ({@link IdentificacaoDoResponsavel}) e o catálogo de grupos. O CPF nunca é copiado
 * para o resultado.
 *
 * <ol>
 *   <li>Tarefa com {@code ASSIGNEE_} do interessado (cadastrante): "Interessado (cadastrante)".</li>
 *   <li>Tarefa com {@code ASSIGNEE_} que está em {@code SERVIDOR}: o nome do servidor, com o papel da etapa e a área do
 *       grupo, se houver.</li>
 *   <li>{@code ASSIGNEE_} sem correspondência (ou que não é um CPF de 11 dígitos): "Responsável não identificado".</li>
 *   <li>Tarefa de grupo sem {@code ASSIGNEE_}: a área do grupo; grupo desconhecido ou {@code 0}: "Equipe
 *       responsável".</li>
 *   <li>Multi-instância (ou mais de uma tarefa no ponto): a lista de participantes, cada um concluiu ou pendente.</li>
 *   <li>Ponto sem tarefa: o papel da etapa no catálogo (a espera de pagamento é do interessado; automática e não
 *       mapeada sem tarefa, "Sistema"; etapa de grupo sem tarefa ativa, "Equipe responsável").</li>
 * </ol>
 */
public final class RegraDoResponsavel {

    /** Só um {@code ASSIGNEE_} com exatamente 11 dígitos é procurado como CPF (como na S1). */
    private static final Pattern CPF = Pattern.compile("\\d{11}");

    private RegraDoResponsavel() {
    }

    /** Identificadores que valem consulta ao SIGA (CPF de 11 dígitos), sem repetição. */
    public static List<IdentificadorDoResponsavel> paraConsultar(Collection<ElementoDaPosicao> pontos) {
        List<IdentificadorDoResponsavel> ids = new ArrayList<>();
        for (ElementoDaPosicao ponto : pontos) {
            ponto.tarefas().forEach(t -> t.responsavel().ifPresent(ids::add));
            ponto.concluidas().forEach(p -> p.responsavel().ifPresent(ids::add));
        }
        return ids.stream().filter(RegraDoResponsavel::pareceCpf).distinct().toList();
    }

    static boolean pareceCpf(IdentificadorDoResponsavel id) {
        return CPF.matcher(id.valorProtegido()).matches();
    }

    public static ResponsavelDaEtapa resolver(ElementoDaPosicao ponto,
            Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificados, CatalogoDeGrupos grupos) {
        Objects.requireNonNull(identificados);
        Optional<PapelDoResponsavel> papel = ponto.etapa().map(Etapa::responsavel);
        List<LeituraDoFluxo.Tarefa> tarefas = ponto.tarefas();
        // Etapa não mapeada com tarefa: não se sabe o papel; "Responsável" e não "Sistema".
        String rotuloDoPapel = papel.map(RegraDoResponsavel::rotulo).orElse(tarefas.isEmpty() ? ResponsavelDaEtapa.SISTEMA
                : "Responsável");

        if (ponto.multiInstancia().isPresent() || tarefas.size() > 1) {
            List<ResponsavelDaEtapa.Participante> participantes = new ArrayList<>();
            ponto.concluidas().forEach(p -> participantes.add(participante(p.responsavel(), rotuloDoPapel, true,
                    identificados)));
            tarefas.forEach(t -> participantes.add(participante(t.responsavel(), rotuloDoPapel, false, identificados)));
            return new ResponsavelDaEtapa(ResponsavelDaEtapa.Tipo.VARIOS, rotuloDoPapel, area(tarefas, grupos),
                    Optional.empty(), participantes, 0);
        }
        if (tarefas.size() == 1) {
            return daTarefa(tarefas.getFirst(), papel.orElse(null), rotuloDoPapel, identificados, grupos);
        }
        return semTarefa(ponto, papel.orElse(null));
    }

    private static ResponsavelDaEtapa daTarefa(LeituraDoFluxo.Tarefa tarefa, PapelDoResponsavel papel, String rotuloDoPapel,
            Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificados, CatalogoDeGrupos grupos) {
        Optional<String> area = area(List.of(tarefa), grupos);
        if (tarefa.responsavel().isPresent()) {
            IdentificacaoDoResponsavel id = identificados.get(tarefa.responsavel().get());
            if (id != null && id.interessado()) {
                return ResponsavelDaEtapa.simples(ResponsavelDaEtapa.Tipo.INTERESSADO, ResponsavelDaEtapa.INTERESSADO,
                        Optional.empty(), Optional.empty());
            }
            if (id != null && id.nomeDoServidor().isPresent()) {
                return ResponsavelDaEtapa.simples(ResponsavelDaEtapa.Tipo.SERVIDOR, rotuloDoPapel, area, id.nomeDoServidor());
            }
            return ResponsavelDaEtapa.simples(ResponsavelDaEtapa.Tipo.NAO_IDENTIFICADO, ResponsavelDaEtapa.NAO_IDENTIFICADO,
                    area, Optional.empty());
        }
        if (area.isPresent()) {
            return ResponsavelDaEtapa.simples(ResponsavelDaEtapa.Tipo.AREA, rotuloDoPapel, area, Optional.empty());
        }
        if (tarefa.grupos().isEmpty() && papel == PapelDoResponsavel.INTERESSADO) {
            return ResponsavelDaEtapa.simples(ResponsavelDaEtapa.Tipo.INTERESSADO, ResponsavelDaEtapa.INTERESSADO,
                    Optional.empty(), Optional.empty());
        }
        return ResponsavelDaEtapa.simples(ResponsavelDaEtapa.Tipo.EQUIPE, ResponsavelDaEtapa.EQUIPE, Optional.empty(),
                Optional.empty());
    }

    private static ResponsavelDaEtapa semTarefa(ElementoDaPosicao ponto, PapelDoResponsavel papel) {
        if (papel == PapelDoResponsavel.INTERESSADO) {
            return ResponsavelDaEtapa.simples(ResponsavelDaEtapa.Tipo.INTERESSADO, ResponsavelDaEtapa.INTERESSADO,
                    Optional.empty(), Optional.empty());
        }
        boolean humana = ponto.etapa().map(e -> e.tipo() == TipoDeEtapa.TAREFA_HUMANA || e.tipo() == TipoDeEtapa.EMISSAO)
                .orElse(false);
        if (papel == null || papel == PapelDoResponsavel.SISTEMA || !humana) {
            return ResponsavelDaEtapa.simples(ResponsavelDaEtapa.Tipo.SISTEMA, ResponsavelDaEtapa.SISTEMA, Optional.empty(),
                    Optional.empty());
        }
        return ResponsavelDaEtapa.simples(ResponsavelDaEtapa.Tipo.EQUIPE, ResponsavelDaEtapa.EQUIPE, Optional.empty(),
                Optional.empty());
    }

    private static ResponsavelDaEtapa.Participante participante(Optional<IdentificadorDoResponsavel> responsavel,
            String rotuloDoPapel, boolean concluiu, Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificados) {
        IdentificacaoDoResponsavel id = responsavel.map(identificados::get).orElse(null);
        if (id != null && id.interessado()) {
            return new ResponsavelDaEtapa.Participante(Optional.empty(), ResponsavelDaEtapa.INTERESSADO, concluiu);
        }
        if (id != null && id.nomeDoServidor().isPresent()) {
            return new ResponsavelDaEtapa.Participante(id.nomeDoServidor(), rotuloDoPapel, concluiu);
        }
        return new ResponsavelDaEtapa.Participante(Optional.empty(),
                responsavel.isPresent() ? ResponsavelDaEtapa.NAO_IDENTIFICADO : rotuloDoPapel, concluiu);
    }

    /** Área do primeiro grupo conhecido entre as tarefas (na ordem lida). */
    private static Optional<String> area(List<LeituraDoFluxo.Tarefa> tarefas, CatalogoDeGrupos grupos) {
        return tarefas.stream().flatMap(t -> t.grupos().stream()).map(grupos::area).flatMap(Optional::stream).findFirst();
    }

    static String rotulo(PapelDoResponsavel papel) {
        return switch (papel) {
            case INTERESSADO -> ResponsavelDaEtapa.INTERESSADO;
            case ANALISTA -> "Analista";
            case CHEFIA -> "Chefia";
            case GRUPO -> ResponsavelDaEtapa.EQUIPE;
            case SISTEMA -> ResponsavelDaEtapa.SISTEMA;
        };
    }
}
