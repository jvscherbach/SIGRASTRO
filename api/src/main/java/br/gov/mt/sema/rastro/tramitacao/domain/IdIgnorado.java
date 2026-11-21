package br.gov.mt.sema.rastro.tramitacao.domain;

/**
 * Atividade do BPMN que nunca vira etapa (regra técnica 10). Fica no catálogo para ser reconhecida, e não confundida com
 * "Etapa não mapeada".
 *
 * @param idTecnico  id da atividade no BPMN
 * @param descricao  o que é (para o Administrador e a documentação)
 * @param motivo     por que é ignorada
 */
public record IdIgnorado(String idTecnico, String descricao, Motivo motivo) {

    public enum Motivo {
        /** Tarefa de suporte técnico: quando ativa, a posição é a última etapa real, com selo (bloco 3). */
        SUPORTE,
        /** Ruído técnico (gateway intermediário, integração, repetição). */
        RUIDO,
        /** Ramo provavelmente inativo. */
        INATIVO
    }
}
