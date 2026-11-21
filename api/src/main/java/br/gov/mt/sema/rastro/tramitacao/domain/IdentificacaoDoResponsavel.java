package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * O que o SIGA diz de um {@code ASSIGNEE_}, sem o CPF: o nome em {@code SERVIDOR} (se houver) e se é o interessado
 * (cadastrante) do requerimento.
 */
public record IdentificacaoDoResponsavel(Optional<String> nomeDoServidor, boolean interessado) {

    public IdentificacaoDoResponsavel {
        Optional<String> nome = Objects.requireNonNullElse(nomeDoServidor, Optional.<String>empty());
        nomeDoServidor = nome.map(String::strip).filter(n -> !n.isEmpty());
    }
}
