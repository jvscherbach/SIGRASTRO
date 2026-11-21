package br.gov.mt.sema.rastro.tramitacao.application.port;

import java.util.Collection;
import java.util.Map;

import br.gov.mt.sema.rastro.tramitacao.domain.IdentificacaoDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;

/**
 * Resolve, em <b>uma</b> consulta por requerimento, o nome em {@code SERVIDOR} e se é o interessado, para os
 * {@code ASSIGNEE_} dados (S7, bloco 4). Implementada só em {@code integration.siga}. Identificador sem correspondência
 * não aparece no mapa. O CPF não volta: a chave é o próprio identificador recebido.
 */
public interface IdentificacaoDosResponsaveis {

    Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificar(long idRequerimento,
            Collection<IdentificadorDoResponsavel> responsaveis);
}
