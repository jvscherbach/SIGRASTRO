package br.gov.mt.sema.rastro.consulta.api.dto;

import java.time.LocalDateTime;

import br.gov.mt.sema.rastro.consulta.domain.ResumoRequerimento;

public record ResultadoBuscaResponse(long id, IdentificacaoResponse identificacao, String numeroRequerimento,
        String numeroProcesso, InteressadoDaListaResponse interessado, String tipoProcesso,
        SituacaoResponse situacao, LocalDateTime abertura) {

    public static ResultadoBuscaResponse de(ResumoRequerimento resumo) {
        return new ResultadoBuscaResponse(resumo.id(), IdentificacaoResponse.de(resumo.identificacao()),
                resumo.numeroRequerimento(), resumo.numeroProcesso(), InteressadoDaListaResponse.de(resumo.interessado()),
                resumo.tipoProcesso(), SituacaoResponse.de(resumo.situacao()), resumo.abertura());
    }
}
