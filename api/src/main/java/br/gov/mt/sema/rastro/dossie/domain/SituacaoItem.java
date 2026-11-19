package br.gov.mt.sema.rastro.dossie.domain;

/** Situações do item (S2.2). OK e REMOVIDO estão resolvidos: uma continuação não os obtém de novo. */
public enum SituacaoItem {
    AGUARDANDO,
    OBTENDO,
    OK,
    FALHA,
    REMOVIDO;

    public boolean resolvido() {
        return this == OK || this == REMOVIDO;
    }
}
