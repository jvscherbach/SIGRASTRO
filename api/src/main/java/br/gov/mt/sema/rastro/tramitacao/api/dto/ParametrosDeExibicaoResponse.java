package br.gov.mt.sema.rastro.tramitacao.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Parâmetros de exibição da tramitação (S7, bloco 10). */
@Schema(description = "Como a tela pede as posições")
public record ParametrosDeExibicaoResponse(
        @Schema(description = "Posições pedidas ao mesmo tempo (1 a 20)") int posicoesSimultaneas) {
}
