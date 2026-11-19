package br.gov.mt.sema.rastro.acesso.domain;

public record Usuario(String login, String nome, Perfil perfil, String cpfCnpj, boolean ativo) {

    /** Só para exibição e retrato (auditoria); decisão de acesso é da política. */
    public String rotuloDoPerfil() {
        return perfil.rotulo();
    }
}
