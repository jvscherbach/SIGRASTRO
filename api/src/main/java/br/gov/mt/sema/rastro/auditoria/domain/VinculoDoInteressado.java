package br.gov.mt.sema.rastro.auditoria.domain;

import java.util.function.Predicate;

/**
 * CPF/CNPJ do interessado guardado no evento só para o filtro do Operador (RN07). Os dígitos não têm leitura pública:
 * saem apenas para a regra de {@link #atende}, e o {@link #toString()} não os mostra. Assim não vão para DTO, CSV ou log
 * por acidente. Valor que não é CPF nem CNPJ vira "sem vínculo", que nenhum Operador enxerga.
 */
public final class VinculoDoInteressado {

    private static final VinculoDoInteressado SEM_VINCULO = new VinculoDoInteressado("");

    private final String digitos;

    private VinculoDoInteressado(String digitos) {
        this.digitos = digitos;
    }

    public static VinculoDoInteressado de(String cpfCnpj) {
        String digitos = cpfCnpj == null ? "" : cpfCnpj.replaceAll("\\D", "");
        return digitos.length() == 11 || digitos.length() == 14 ? new VinculoDoInteressado(digitos) : SEM_VINCULO;
    }

    /** Aplica a regra de acesso aos dígitos; quem decide é a política, não o evento. */
    public boolean atende(Predicate<String> regra) {
        return regra.test(digitos);
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof VinculoDoInteressado vinculo && digitos.equals(vinculo.digitos);
    }

    @Override
    public int hashCode() {
        return digitos.hashCode();
    }

    @Override
    public String toString() {
        return "VinculoDoInteressado[***]";
    }
}
