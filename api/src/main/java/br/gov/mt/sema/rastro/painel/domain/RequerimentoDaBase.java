package br.gov.mt.sema.rastro.painel.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Um requerimento protocolado no ano (a unidade de contagem do painel é o {@code REQUERIMENTO.ID}, nunca o número do
 * processo, que não é único).
 *
 * @param id             {@code REQUERIMENTO.ID}
 * @param tipoDeProcesso {@code TIPO_PROCESSO.INSTANCIA_REQUERIMENTO}, que escolhe o modelo no catálogo de etapas
 * @param situacao       {@code SITUACAO_PROCESSO}, crua (concluído, cancelado e arquivado saem da fila no bloco 3)
 */
public record RequerimentoDaBase(long id, Optional<String> tipoDeProcesso, Optional<String> situacao) {

    public RequerimentoDaBase {
        Objects.requireNonNull(tipoDeProcesso);
        Objects.requireNonNull(situacao);
    }
}
