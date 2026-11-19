package br.gov.mt.sema.rastro.dossie.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.rastro.dossie.application.AndamentoDoDossie;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Andamento do pedido (RF16, S2.2). {@code falhas} lista os documentos com problema (nome e motivo) quando o pedido
 * aguarda decisão; {@code erro} traz o motivo geral (credencial recusada, com {@code codigo}, ou falha na montagem).
 * {@code tamanhoBytes} e {@code entregaPorTicket} (acima de DOSSIE_BLOB_MAX_MB: download por ticket, sem
 * pré-visualização) só valem em CONCLUIDO. Nenhum campo expõe a chave do arquivo.
 */
public record AndamentoResponse(String id, String status, int total, int processados, ErroResponse erro,
        List<FalhaResponse> falhas, Integer paginas, Long tamanhoBytes, boolean entregaPorTicket, LocalDateTime expiraEm,
        List<ItemResponse> itens) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErroResponse(String origem, String id, String titulo, String motivo, String codigo) {
    }

    public record FalhaResponse(String origem, String id, String titulo, String motivo) {
    }

    public record ItemResponse(String origem, String id, String titulo, String situacao, int tentativas,
            @JsonInclude(JsonInclude.Include.NON_NULL) String motivo) {
    }

    public static AndamentoResponse de(AndamentoDoDossie a) {
        ErroResponse erro = a.erro() == null ? null
                : new ErroResponse(a.erro().origem(), a.erro().id(), a.erro().titulo(), a.erro().motivo(),
                        a.erro().codigo());
        return new AndamentoResponse(a.id(), a.status().name(), a.total(), a.processados(), erro,
                a.falhas().stream().map(f -> new FalhaResponse(f.origem(), f.id(), f.titulo(), f.motivo())).toList(),
                a.paginas(), a.tamanhoBytes(), a.entregaPorTicket(), a.expiraEm(),
                a.itens().stream().map(i -> new ItemResponse(i.origem(), i.id(), i.titulo(), i.situacao().name(),
                        i.tentativas(), i.motivo())).toList());
    }
}
