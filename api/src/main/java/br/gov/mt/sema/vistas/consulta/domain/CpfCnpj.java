package br.gov.mt.sema.vistas.consulta.domain;

import br.gov.mt.sema.vistas.shared.application.exception.ParametroInvalidoException;

/** CPF (11 dígitos) ou CNPJ (14 dígitos), guardado só com dígitos. */
public record CpfCnpj(String digitos) {

    public static CpfCnpj de(String texto) {
        String digitos = texto == null ? "" : texto.replaceAll("\\D", "");
        if (digitos.length() != 11 && digitos.length() != 14) {
            throw new ParametroInvalidoException("cpfCnpj", "Informe um CPF (11 dígitos) ou CNPJ (14 dígitos).");
        }
        return new CpfCnpj(digitos);
    }
}
