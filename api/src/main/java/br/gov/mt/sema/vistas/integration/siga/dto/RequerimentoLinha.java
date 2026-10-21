package br.gov.mt.sema.vistas.integration.siga.dto;

import java.time.LocalDateTime;

/** Linha das consultas de requerimento. Tipo externo: não sai de integration.siga. */
public record RequerimentoLinha(long id, String numero, String numeroProcesso, LocalDateTime dataAbertura,
        LocalDateTime dataAlteracao, String situacao, String tipoProcesso, String tipoRequerimento,
        String nomeInteressado, String cpfCnpjInteressado) {
}
