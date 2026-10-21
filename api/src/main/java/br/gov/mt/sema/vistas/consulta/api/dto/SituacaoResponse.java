package br.gov.mt.sema.vistas.consulta.api.dto;

import br.gov.mt.sema.vistas.consulta.domain.Situacao;

public record SituacaoResponse(String codigo, String descricao) {

    public static SituacaoResponse de(Situacao situacao) {
        return new SituacaoResponse(situacao.codigo(), situacao.descricao());
    }
}
