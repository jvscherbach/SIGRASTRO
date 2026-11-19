package br.gov.mt.sema.rastro.dossie.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Corpo do POST /dossies. Os documentos são referenciados por origem e id; a chave do arquivo nunca trafega. */
public record CriarDossieRequest(
        @NotNull(message = "Informe o requerimento.") @Positive(message = "Requerimento inválido.") Long idRequerimento,
        @NotEmpty(message = "Selecione ao menos um documento.") List<@Valid @NotNull DocumentoRef> documentos) {

    public record DocumentoRef(@NotNull(message = "Informe a origem.") String origem,
            @NotNull(message = "Informe o id do documento.") String id) {
    }
}
