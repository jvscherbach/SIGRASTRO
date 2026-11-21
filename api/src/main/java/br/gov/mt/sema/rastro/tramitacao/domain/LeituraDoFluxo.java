package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fatos crus lidos do Camunda para um requerimento e uma chave de modelo, sem interpretação: quem interpreta é
 * {@link MontadorDaPosicao}, pelo catálogo. Horários em relógio de parede de Cuiabá (como o SIGA grava).
 *
 * @param instancias    instâncias raiz com a chave do modelo e {@code BUSINESS_KEY_} = id do requerimento, da mais
 *                      recente para a mais antiga (por {@code START_TIME_}); vazia = sem instância
 * @param execucoes     execuções da instância mais recente com atividade ({@code ACT_RU_EXECUTION.ACT_ID_}); vazia se
 *                      encerrada
 * @param tarefas       tarefas ativas da instância mais recente e das filhas
 * @param filhas        instâncias filhas ativas (emissões), ligadas pela instância-mãe
 * @param complementacoes instâncias de complementação ativas do requerimento
 * @param percorridas   atividades encerradas da instância mais recente (tarefa só se concluída), da mais recente para a
 *                      mais antiga
 */
public record LeituraDoFluxo(
        List<InstanciaRaiz> instancias,
        List<Execucao> execucoes,
        List<Tarefa> tarefas,
        List<Filha> filhas,
        List<Complementacao> complementacoes,
        List<Percorrida> percorridas) {

    public LeituraDoFluxo {
        instancias = List.copyOf(Objects.requireNonNullElse(instancias, List.of()));
        execucoes = List.copyOf(Objects.requireNonNullElse(execucoes, List.of()));
        tarefas = List.copyOf(Objects.requireNonNullElse(tarefas, List.of()));
        filhas = List.copyOf(Objects.requireNonNullElse(filhas, List.of()));
        complementacoes = List.copyOf(Objects.requireNonNullElse(complementacoes, List.of()));
        percorridas = List.copyOf(Objects.requireNonNullElse(percorridas, List.of()));
    }

    public static LeituraDoFluxo semInstancia() {
        return new LeituraDoFluxo(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** Só as instâncias (a mais recente está encerrada: nada mais é lido). */
    public static LeituraDoFluxo encerrada(List<InstanciaRaiz> instancias) {
        return new LeituraDoFluxo(instancias, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public Optional<InstanciaRaiz> maisRecente() {
        return instancias.stream().findFirst();
    }

    /**
     * Instância raiz ({@code ACT_HI_PROCINST}).
     *
     * @param estado           {@code STATE_}, só para diagnóstico interno
     * @param comMotivoDeExclusao {@code DELETE_REASON_} preenchido (o texto não é guardado: pode conter texto livre)
     */
    public record InstanciaRaiz(String id, LocalDateTime inicio, LocalDateTime fim, String atividadeFinal, String estado,
            boolean comMotivoDeExclusao) {

        public boolean encerrada() {
            return fim != null;
        }
    }

    /**
     * Execução com atividade ({@code ACT_RU_EXECUTION.ACT_ID_}).
     *
     * @param desde      início da atividade aberta em {@code ACT_HI_ACTINST}, se houver
     * @param assinadaEm criação da assinatura de evento ({@code ACT_RU_EVENT_SUBSCR}) da execução; nula = sem assinatura
     */
    public record Execucao(String idTecnico, LocalDateTime desde, LocalDateTime assinadaEm) {}

    /**
     * Tarefa ativa ({@code ACT_RU_TASK}).
     *
     * @param instancia   {@code PROC_INST_ID_} (da raiz ou de uma filha)
     * @param responsavel {@code ASSIGNEE_} protegido (CPF); vazio = sem responsável
     * @param grupos      grupos candidatos ({@code ACT_RU_IDENTITYLINK}, {@code TYPE_ = 'candidate'})
     */
    public record Tarefa(String instancia, String idTecnico, LocalDateTime criadaEm,
            Optional<IdentificadorDoResponsavel> responsavel, List<String> grupos) {

        public Tarefa {
            responsavel = Objects.requireNonNullElse(responsavel, Optional.empty());
            grupos = List.copyOf(Objects.requireNonNullElse(grupos, List.of()));
        }
    }

    /** Instância filha ativa; {@code chave} é a chave da definição dela ({@code procSurh*}). */
    public record Filha(String id, String chave, LocalDateTime inicio) {}

    /** Instância de complementação ativa (chave {@code procGestaoComplementacao}). */
    public record Complementacao(String id, LocalDateTime inicio) {}

    /**
     * Atividade encerrada da instância ({@code ACT_HI_ACTINST} com {@code END_TIME_}; se for tarefa, só com
     * {@code DELETE_REASON_ = 'completed'}).
     */
    public record Percorrida(String idTecnico, LocalDateTime inicio, LocalDateTime fim, boolean tarefa,
            Optional<IdentificadorDoResponsavel> responsavel) {

        public Percorrida {
            responsavel = Objects.requireNonNullElse(responsavel, Optional.empty());
        }
    }
}
