package br.gov.mt.sema.rastro.financeiro.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * O que o financeiro precisa de uma passagem da tramitação (S9, blocos 4 e 5): o id da etapa do catálogo (vazio = não
 * mapeada), se é do ramo de correção e a entrada. Cópia mínima, para o domínio do financeiro não depender do modelo da
 * tramitação.
 */
public record PassagemDoFluxo(Optional<String> idDaEtapa, boolean correcao, LocalDateTime entrada) {

    public PassagemDoFluxo {
        idDaEtapa = Objects.requireNonNullElse(idDaEtapa, Optional.empty());
        Objects.requireNonNull(entrada, "entrada");
    }

    public boolean eh(String etapa) {
        return idDaEtapa.filter(etapa::equals).isPresent();
    }
}
