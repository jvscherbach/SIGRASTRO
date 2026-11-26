package br.gov.mt.sema.rastro.caixa.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Um requerimento na caixa de um analista (S11, bloco 5). Só o nome do interessado (nunca CPF/CNPJ) e nenhum campo de CPF
 * do analista: quem é o dono é a chave opaca do instantâneo.
 *
 * @param estado       {@code COM_ANALISTA} ou {@code COM_INTERESSADO}; nunca {@code FORA} (o que está fora não entra)
 * @param inicioDaEtapa início da tarefa atual (para a correção, o da tarefa de correção); vazio = sem informação
 */
public record ItemDaCaixa(long id, Optional<String> numero, Optional<String> interessado, Optional<String> tipo,
        EstadoDePosse estado, Optional<LocalDateTime> inicioDaEtapa) {

    /** Cuiabá, o relógio de parede do SIGA e do Camunda. */
    public static final ZoneId FUSO = ZoneId.of("America/Cuiaba");

    public ItemDaCaixa {
        Objects.requireNonNull(numero);
        Objects.requireNonNull(interessado);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(estado);
        Objects.requireNonNull(inicioDaEtapa);
        if (estado.tipo() == EstadoDePosse.Tipo.FORA) {
            throw new IllegalArgumentException("o que está fora da posse não entra na caixa");
        }
    }

    /** Dias completos na etapa até {@code agora}; vazio = sem informação. Relógio adiantado nunca dá negativo. */
    public Optional<Long> diasNaEtapa(Instant agora) {
        return inicioDaEtapa.map(inicio -> {
            long dias = ChronoUnit.DAYS.between(inicio, LocalDateTime.ofInstant(agora, FUSO));
            return Math.max(0, dias);
        });
    }
}
