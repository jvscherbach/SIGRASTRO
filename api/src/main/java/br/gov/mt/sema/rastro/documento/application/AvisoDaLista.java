package br.gov.mt.sema.rastro.documento.application;

/** Algo que não impediu a listagem, mas o usuário precisa saber (S4). Mensagem exibível, sem detalhe técnico. */
public record AvisoDaLista(String codigo, String mensagem) {

    public static final String SCORP_INDISPONIVEL = "SCORP_INDISPONIVEL";
    public static final String SCORP_PARCIAL = "SCORP_PARCIAL";

    static AvisoDaLista scorpIndisponivel() {
        return new AvisoDaLista(SCORP_INDISPONIVEL, "Os documentos pessoais (SCORP) não puderam ser consultados agora. "
                + "Os demais documentos foram listados.");
    }

    static AvisoDaLista scorpParcial() {
        return new AvisoDaLista(SCORP_PARCIAL, "Os documentos pessoais (SCORP) de parte das pessoas do requerimento não "
                + "puderam ser consultados agora. Os demais documentos foram listados.");
    }
}
