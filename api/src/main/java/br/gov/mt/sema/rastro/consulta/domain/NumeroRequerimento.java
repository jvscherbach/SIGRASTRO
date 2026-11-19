package br.gov.mt.sema.rastro.consulta.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;

/** Número no formato N/AAAA (1 a 6 dígitos, barra e ano com 4 dígitos). Vale para requerimento e processo. */
public record NumeroRequerimento(int numero, int ano) {

    private static final Pattern FORMATO = Pattern.compile("(\\d{1,6})/(\\d{4})");

    public static NumeroRequerimento de(String texto) {
        Matcher m = texto == null ? null : FORMATO.matcher(texto.trim());
        if (m == null || !m.matches()) {
            throw new ParametroInvalidoException("numero", "Informe o número no formato N/AAAA.");
        }
        return new NumeroRequerimento(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    public String texto() {
        return numero + "/" + ano;
    }
}
