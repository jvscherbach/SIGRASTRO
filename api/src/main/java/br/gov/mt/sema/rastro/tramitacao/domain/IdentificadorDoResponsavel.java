package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Valor cru de {@code ASSIGNEE_} do Camunda, que no SIGA é o <b>CPF</b> do servidor ou do interessado. Existe só para o
 * bloco 4 resolver o nome (por porta, em {@code integration.siga}); nunca vai para resposta, log, erro, auditoria nem
 * chave de cache. {@link #toString()} não mostra o valor, para que um log descuidado de um record que o contenha não o
 * vaze.
 */
public final class IdentificadorDoResponsavel {

    private final String valor;

    private IdentificadorDoResponsavel(String valor) {
        this.valor = valor;
    }

    /** Vazio quando o {@code ASSIGNEE_} é nulo ou em branco (tarefa de grupo ainda não assumida). */
    public static Optional<IdentificadorDoResponsavel> de(String assignee) {
        return assignee == null || assignee.isBlank()
                ? Optional.empty()
                : Optional.of(new IdentificadorDoResponsavel(assignee.strip()));
    }

    /** Uso restrito: só a resolução do responsável (bloco 4) lê o valor, para consultar {@code SERVIDOR}. */
    public String valorProtegido() {
        return valor;
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof IdentificadorDoResponsavel o && o.valor.equals(valor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valor);
    }

    @Override
    public String toString() {
        return "IdentificadorDoResponsavel[protegido]";
    }
}
