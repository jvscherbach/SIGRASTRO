package br.gov.mt.sema.rastro.dossie.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Decisão sobre um pedido em PENDENTE_DECISAO (S2.2). */
public record ContinuacaoRequest(
        @Schema(description = "TENTAR_NOVAMENTE (refaz só os documentos com problema) ou REMOVER_E_CONTINUAR "
                + "(remove-os e monta com os já obtidos)", allowableValues = {"TENTAR_NOVAMENTE", "REMOVER_E_CONTINUAR"})
        String acao) {
}
