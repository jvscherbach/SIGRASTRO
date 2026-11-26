package br.gov.mt.sema.rastro.acesso.domain;

/**
 * @param senhaHash hash da senha ({@link HashDeSenha}); {@code null} = usuário sem senha, que não consegue entrar. Nunca
 *         sai em DTO, em log nem em {@code toString}.
 */
public record Usuario(String login, String nome, Perfil perfil, String cpfCnpj, boolean ativo, String senhaHash) {

    /** Usuário sem senha definida. */
    public Usuario(String login, String nome, Perfil perfil, String cpfCnpj, boolean ativo) {
        this(login, nome, perfil, cpfCnpj, ativo, null);
    }

    public Usuario comSenhaHash(String novoHash) {
        return new Usuario(login, nome, perfil, cpfCnpj, ativo, novoHash);
    }

    /** Só para exibição e retrato (auditoria); decisão de acesso é da política. */
    public String rotuloDoPerfil() {
        return perfil.rotulo();
    }

    /** Sem hash e sem CPF/CNPJ: o objeto pode ir parar num log sem vazar nada. */
    @Override
    public String toString() {
        return "Usuario[login=" + login + ", perfil=" + perfil + ", ativo=" + ativo + "]";
    }
}
