package br.gov.mt.sema.rastro.caixa.domain;

import java.util.Objects;

import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;

/**
 * Um usuário Analista com vínculo: o identificador no Camunda (o {@code ASSIGNEE_}, que nunca sai do adaptador) e a chave
 * opaca que representa o analista no resto do sistema.
 */
public record AnalistaParaLeitura(ChaveDoResponsavel chave, IdentificadorDoResponsavel identificador) {

    public AnalistaParaLeitura {
        Objects.requireNonNull(chave);
        Objects.requireNonNull(identificador);
    }
}
