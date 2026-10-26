package br.gov.mt.sema.vistas.dossie.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.vistas.dossie.application.AndamentoDoDossie;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Andamento do pedido (RF16). O erro traz o documento e o motivo; nenhum campo expõe a chave do arquivo. */
public record AndamentoResponse(String id, String status, int total, int processados, ErroResponse erro,
        Integer paginas, LocalDateTime expiraEm, List<ItemResponse> itens) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErroResponse(String origem, String id, String titulo, String motivo, String codigo) {
    }

    public record ItemResponse(String origem, String id, String titulo, String situacao, int tentativas) {
    }

    public static AndamentoResponse de(AndamentoDoDossie a) {
        ErroResponse erro = a.erro() == null ? null
                : new ErroResponse(a.erro().origem(), a.erro().id(), a.erro().titulo(), a.erro().motivo(),
                        a.erro().codigo());
        return new AndamentoResponse(a.id(), a.status().name(), a.total(), a.processados(), erro, a.paginas(),
                a.expiraEm(), a.itens().stream()
                        .map(i -> new ItemResponse(i.origem(), i.id(), i.titulo(), i.situacao().name(), i.tentativas())).toList());
    }
}
