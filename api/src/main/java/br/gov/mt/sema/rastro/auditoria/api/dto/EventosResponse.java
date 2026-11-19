package br.gov.mt.sema.rastro.auditoria.api.dto;

import br.gov.mt.sema.rastro.auditoria.application.IndicadoresDoPeriodo;
import br.gov.mt.sema.rastro.auditoria.application.PaginaDeEventos;
import br.gov.mt.sema.rastro.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.rastro.shared.api.PageResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** {@link PageResponse} mais os indicadores do período. */
public record EventosResponse(@JsonUnwrapped PageResponse<EventoResponse> pagina, Indicadores indicadores) {

    /** Só os do grupo: documentos ou acessos. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Indicadores(Long dossiesGerados, Long eventosComSigilosos, Long entradas, Long alteracoes,
            long usuarios) {

        static Indicadores de(GrupoDeEvento grupo, IndicadoresDoPeriodo i) {
            return grupo == GrupoDeEvento.DOCUMENTOS
                    ? new Indicadores(i.dossiesGerados(), i.eventosComSigilosos(), null, null, i.usuarios())
                    : new Indicadores(null, null, i.entradas(), i.alteracoes(), i.usuarios());
        }
    }

    public static EventosResponse de(GrupoDeEvento grupo, PaginaDeEventos pagina) {
        return new EventosResponse(PageResponse.of(pagina.itens().stream().map(EventoResponse::de).toList(),
                pagina.pagina(), pagina.tamanho(), pagina.total()), Indicadores.de(grupo, pagina.indicadores()));
    }
}
