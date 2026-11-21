package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Uma linha de {@code ACT_HI_ACTINST} (com a tarefa de {@code ACT_HI_TASKINST}, se for tarefa), <b>sem interpretação</b>
 * (S8, bloco 2): ruído, repetições e tarefas encerradas sem ação continuam aqui; quem decide o que é etapa percorrida é a
 * reconstrução (bloco 3). Horários em relógio de parede de Cuiabá.
 *
 * <p>Os ids técnicos ({@code id}, {@code idPai}, {@code idExecucao}, {@code idTecnico}, {@code instancia}) só servem à
 * reconstrução e ao diagnóstico do Administrador. O {@code ASSIGNEE_} (CPF) vai protegido
 * ({@link IdentificadorDoResponsavel}) e sai da resposta depois da resolução do responsável.
 *
 * @param instancia   {@code PROC_INST_ID_} (da raiz, de uma filha ou da complementação)
 * @param origem      papel dessa instância no requerimento
 * @param id          {@code ACT_HI_ACTINST.ID_}; no histórico, ordenar por início e id dá a ordem de gravação
 * @param idPai       {@code PARENT_ACT_INST_ID_}; pode vir vazio (o corpo da multi-instância nem sempre é o pai: NÃO
 *                    CONFIRMADO, P40)
 * @param idExecucao  {@code EXECUTION_ID_}
 * @param idTecnico   {@code ACT_ID_}
 * @param nome        {@code ACT_NAME_}; só diagnóstico, o rótulo mostrado vem do catálogo
 * @param tipo        {@code ACT_TYPE_} ({@code userTask}, {@code serviceTask}, {@code callActivity},
 *                    {@code multiInstanceBody}...)
 * @param fim         vazio = atividade aberta
 * @param responsavel {@code ASSIGNEE_} da atividade
 * @param chamada     {@code CALL_PROC_INST_ID_}: a filha que a {@code callActivity} chamou
 * @param tarefa      a tarefa humana ligada à atividade ({@code TASK_ID_}); vazia nas demais
 */
public record AtividadeBruta(
        String instancia,
        OrigemDaInstancia origem,
        String id,
        String idPai,
        String idExecucao,
        String idTecnico,
        String nome,
        String tipo,
        LocalDateTime inicio,
        LocalDateTime fim,
        Optional<IdentificadorDoResponsavel> responsavel,
        String chamada,
        Optional<TarefaBruta> tarefa) {

    public AtividadeBruta {
        Objects.requireNonNull(instancia, "instancia");
        Objects.requireNonNull(origem, "origem");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(idTecnico, "idTecnico");
        Objects.requireNonNull(inicio, "inicio");
        responsavel = Objects.requireNonNullElse(responsavel, Optional.empty());
        tarefa = Objects.requireNonNullElse(tarefa, Optional.empty());
    }

    public boolean aberta() {
        return fim == null;
    }

    /**
     * Linha de {@code ACT_HI_TASKINST}.
     *
     * @param definicao {@code TASK_DEF_KEY_}
     * @param motivo    {@code DELETE_REASON_} classificado; o texto original não sai (pode ser texto livre)
     */
    public record TarefaBruta(String definicao, Optional<IdentificadorDoResponsavel> responsavel, LocalDateTime inicio,
            LocalDateTime fim, MotivoDaTarefa motivo) {

        public TarefaBruta {
            responsavel = Objects.requireNonNullElse(responsavel, Optional.empty());
            Objects.requireNonNull(motivo, "motivo");
        }
    }

    /** {@code DELETE_REASON_} de {@code ACT_HI_TASKINST}, reduzido ao que a regra técnica 10 distingue. */
    public enum MotivoDaTarefa {
        /** Sem motivo: a tarefa ainda está aberta. */
        ABERTA,
        /** {@code completed}: a tarefa foi feita (percorrida). */
        CONCLUIDA,
        /** {@code deleted}: encerrada sem ação (condição de término, borda, recusa): não conta como percorrida. */
        EXCLUIDA,
        /** Qualquer outro texto: não é percorrida; o texto não é guardado. */
        OUTRO
    }
}
