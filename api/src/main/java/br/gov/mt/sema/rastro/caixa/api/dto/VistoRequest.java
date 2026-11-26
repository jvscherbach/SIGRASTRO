package br.gov.mt.sema.rastro.caixa.api.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** O conjunto de requerimentos que a tela recebeu em {@code GET /api/caixa/avisos}; só esse conjunto vira "visto". */
@Schema(description = "Ids dos requerimentos devolvidos pela lista de mudanças.")
public record VistoRequest(List<Long> ids) {
}
