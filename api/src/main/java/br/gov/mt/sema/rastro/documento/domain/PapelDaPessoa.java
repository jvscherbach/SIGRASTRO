package br.gov.mt.sema.rastro.documento.domain;

/**
 * Papel da pessoa no requerimento, para os documentos pessoais do SCORP (S4). A ordem é a de precedência: a pessoa
 * que tem mais de um papel é consultada uma vez e seus documentos levam o primeiro papel desta lista.
 */
public enum PapelDaPessoa {

    INTERESSADO("Interessado"),
    REPRESENTANTE_LEGAL("Representante legal"),
    RESPONSAVEL_TECNICO("Responsável técnico");

    private final String descricao;

    PapelDaPessoa(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }

    public static PapelDaPessoa de(String codigo) {
        for (PapelDaPessoa papel : values()) {
            if (papel.name().equals(codigo)) {
                return papel;
            }
        }
        return null;
    }
}
