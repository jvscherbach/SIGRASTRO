package br.gov.mt.sema.rastro.consulta.api.dto;

import java.time.LocalDateTime;

import br.gov.mt.sema.rastro.consulta.application.DetalheDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;

public record RequerimentoResponse(long id, IdentificacaoResponse identificacao, String numeroRequerimento,
        String numeroProcesso, String tipoRequerimento, PrioridadeResponse prioridade, SituacaoResponse situacao,
        LocalDateTime abertura, LocalDateTime atualizacao, InteressadoResponse interessado, String tipoProcesso,
        ObjetoResponse objeto, TarefaAtualResponse tarefaAtual) {

    public static RequerimentoResponse de(DetalheDoRequerimento detalhe) {
        Requerimento r = detalhe.requerimento();
        return new RequerimentoResponse(r.id(), IdentificacaoResponse.de(r.identificacao()), r.numeroRequerimento(),
                r.numeroProcesso(), r.tipoRequerimento(), PrioridadeResponse.de(detalhe.prioridade()),
                SituacaoResponse.de(r.situacao()), r.abertura(), r.atualizacao(),
                InteressadoResponse.de(r.interessado()), r.tipoProcesso(), ObjetoResponse.de(detalhe.objeto()),
                TarefaAtualResponse.de(detalhe.tarefaAtual()));
    }
}
