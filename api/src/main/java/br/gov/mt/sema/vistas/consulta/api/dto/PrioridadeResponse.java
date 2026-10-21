package br.gov.mt.sema.vistas.consulta.api.dto;

import java.util.List;

import br.gov.mt.sema.vistas.consulta.domain.PrioridadeAtendimento;

public record PrioridadeResponse(PrioridadeAtendimento.Tipo codigo, String descricao, List<CriterioResponse> criterios) {

    public record CriterioResponse(int codigo, String descricao) {
    }

    public static PrioridadeResponse de(PrioridadeAtendimento prioridade) {
        return new PrioridadeResponse(prioridade.tipo(), prioridade.descricao(),
                prioridade.criterios().stream().map(c -> new CriterioResponse(c.codigo(), c.descricao())).toList());
    }
}
