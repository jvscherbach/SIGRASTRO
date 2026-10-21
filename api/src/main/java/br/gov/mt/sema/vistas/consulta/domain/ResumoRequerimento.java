package br.gov.mt.sema.vistas.consulta.domain;

import java.time.LocalDateTime;

/** Item de lista de resultados. */
public record ResumoRequerimento(long id, String numeroRequerimento, String numeroProcesso, Interessado interessado,
        String tipoProcesso, Situacao situacao, LocalDateTime abertura) {

    public Identificacao identificacao() {
        return Identificacao.de(numeroRequerimento, numeroProcesso);
    }
}
