package br.gov.mt.sema.vistas.acesso.domain;

/**
 * RN07: restrição de acesso pelo vínculo com o interessado. Perfis restritos (Operador) só acessam o que tem
 * o CPF/CNPJ do próprio usuário como interessado; os demais não têm restrição.
 *
 * @param digitos somente dígitos do CPF/CNPJ do usuário restrito (vazio quando não há cadastro)
 */
public record RestricaoDeInteressado(boolean restrita, String digitos) {

    public static RestricaoDeInteressado nenhuma() {
        return new RestricaoDeInteressado(false, "");
    }

    public static RestricaoDeInteressado de(Usuario usuario) {
        if (!usuario.perfil().restritoAoInteressado()) {
            return nenhuma();
        }
        return new RestricaoDeInteressado(true, somenteDigitos(usuario.cpfCnpj()));
    }

    /** Usuário restrito sem CPF/CNPJ cadastrado não enxerga nada. */
    public boolean semAcessoAlgum() {
        return restrita && digitos.isEmpty();
    }

    public boolean permite(String cpfCnpjDoInteressado) {
        if (!restrita) {
            return true;
        }
        return !digitos.isEmpty() && digitos.equals(somenteDigitos(cpfCnpjDoInteressado));
    }

    private static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }
}
