package br.gov.mt.sema.vistas.consulta.api.dto;

import br.gov.mt.sema.vistas.consulta.domain.Interessado;

public record InteressadoResponse(String nome, String cpfCnpj) {

    public static InteressadoResponse de(Interessado interessado) {
        return interessado == null ? null : new InteressadoResponse(interessado.nome(), interessado.cpfCnpj());
    }
}
