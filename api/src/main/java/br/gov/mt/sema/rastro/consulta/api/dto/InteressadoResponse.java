package br.gov.mt.sema.rastro.consulta.api.dto;

import br.gov.mt.sema.rastro.consulta.domain.Interessado;

public record InteressadoResponse(String nome, String cpfCnpj) {

    public static InteressadoResponse de(Interessado interessado) {
        return interessado == null ? null : new InteressadoResponse(interessado.nome(), interessado.cpfCnpj());
    }
}
