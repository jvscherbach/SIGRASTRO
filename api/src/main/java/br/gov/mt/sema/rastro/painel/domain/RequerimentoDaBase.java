package br.gov.mt.sema.rastro.painel.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Um requerimento protocolado no ano (a unidade de contagem do painel é o {@code REQUERIMENTO.ID}, nunca o número do
 * processo, que não é único).
 *
 * @param id             {@code REQUERIMENTO.ID}
 * @param tipoDeProcesso {@code TIPO_PROCESSO.INSTANCIA_REQUERIMENTO}, que escolhe o modelo no catálogo de etapas
 * @param situacao       {@code SITUACAO_PROCESSO}, crua (concluído, cancelado e arquivado saem da fila no bloco 3)
 * @param numero         {@code REQUERIMENTO.NUMERO}, para a lista por analista (S11, bloco 9)
 * @param interessado    só o nome (ou a razão social) do interessado; nunca CPF/CNPJ (S11, bloco 9)
 * @param protocolo      data de protocolo (dia de Cuiabá), para a lista por analista
 */
public record RequerimentoDaBase(long id, Optional<String> tipoDeProcesso, Optional<String> situacao,
        Optional<String> numero, Optional<String> interessado, Optional<LocalDate> protocolo) {

    public RequerimentoDaBase {
        Objects.requireNonNull(tipoDeProcesso);
        Objects.requireNonNull(situacao);
        Objects.requireNonNull(numero);
        Objects.requireNonNull(interessado);
        Objects.requireNonNull(protocolo);
    }

    /** Sem os dados da lista (usado onde só a contagem importa). */
    public RequerimentoDaBase(long id, Optional<String> tipoDeProcesso, Optional<String> situacao) {
        this(id, tipoDeProcesso, situacao, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
