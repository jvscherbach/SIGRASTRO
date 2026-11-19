package br.gov.mt.sema.rastro.consulta.api.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.gov.mt.sema.rastro.consulta.domain.TarefaAtual;

public record TarefaAtualResponse(TarefaAtual.Estado estado, String nome, LocalDateTime desde,
        @JsonInclude(JsonInclude.Include.NON_NULL) ResponsavelResponse responsavel, String fluxo) {

    public static TarefaAtualResponse de(TarefaAtual tarefa) {
        return new TarefaAtualResponse(tarefa.estado(), tarefa.nome(), tarefa.desde(),
                ResponsavelResponse.de(tarefa.responsavel()), tarefa.fluxo());
    }
}
