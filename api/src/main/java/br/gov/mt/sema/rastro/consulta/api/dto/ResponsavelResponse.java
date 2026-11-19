package br.gov.mt.sema.rastro.consulta.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.gov.mt.sema.rastro.consulta.domain.ResponsavelTarefa;

/** Responsável pela tarefa: só situação e nome. O CPF do servidor nunca sai do adapter. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponsavelResponse(ResponsavelTarefa.Situacao situacao, String nome) {

    public static ResponsavelResponse de(ResponsavelTarefa responsavel) {
        return responsavel == null ? null : new ResponsavelResponse(responsavel.situacao(), responsavel.nome());
    }
}
