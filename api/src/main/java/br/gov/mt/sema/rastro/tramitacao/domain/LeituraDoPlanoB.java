package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fatos crus do Licenciamento para o plano B (S8, bloco 5): requerimento sem instância no Camunda. Só eventos de
 * {@code HISTORICO_REQUERIMENTO} (data e nome do evento; <b>nunca</b> {@code CPF_USUARIO}) e datas e situação de
 * {@code REQUERIMENTO}. Nenhum responsável: o plano B não consulta {@code SERVIDOR}.
 *
 * @param eventos          em ordem de data (e id)
 * @param eventosTruncados havia mais eventos que o limite lido
 */
public record LeituraDoPlanoB(Optional<String> situacao, Datas datas, List<Evento> eventos, boolean eventosTruncados) {

    public LeituraDoPlanoB {
        situacao = Objects.requireNonNullElse(situacao, Optional.<String>empty()).map(String::strip)
                .filter(s -> !s.isEmpty());
        datas = Objects.requireNonNullElse(datas, Datas.NENHUMA);
        eventos = List.copyOf(Objects.requireNonNullElse(eventos, List.of()));
    }

    /** Uma linha de {@code HISTORICO_REQUERIMENTO}: só data e evento. */
    public record Evento(String evento, LocalDateTime quando) {
        public Evento {
            Objects.requireNonNull(evento, "evento");
            Objects.requireNonNull(quando, "quando");
        }
    }

    /** Datas de {@code REQUERIMENTO}; nula = não preenchida. */
    public record Datas(LocalDateTime abertura, LocalDateTime protocolo, LocalDateTime cancelamento,
            LocalDateTime expiracaoDoRequerimento, LocalDateTime expiracaoDoProcesso) {

        public static final Datas NENHUMA = new Datas(null, null, null, null, null);
    }
}
