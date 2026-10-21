package br.gov.mt.sema.vistas.consulta.domain;

import java.time.LocalDateTime;

/** Dados do requerimento (RF04). {@code tipoRequerimento} é nulo quando o SIGA não o informa. */
public record Requerimento(long id, String numeroRequerimento, String numeroProcesso, String tipoRequerimento,
        Situacao situacao, LocalDateTime abertura, LocalDateTime atualizacao, Interessado interessado,
        String tipoProcesso) {

    public Identificacao identificacao() {
        return Identificacao.de(numeroRequerimento, numeroProcesso);
    }

    public ResumoRequerimento resumo() {
        return new ResumoRequerimento(id, numeroRequerimento, numeroProcesso, interessado, tipoProcesso, situacao,
                abertura);
    }

    public String cpfCnpjDoInteressado() {
        return interessado == null ? null : interessado.cpfCnpj();
    }
}
