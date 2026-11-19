package br.gov.mt.sema.rastro.dossie.application.port;

/** Fila em memória com executor limitado a {@code DOSSIE_THREADS} gerações simultâneas. */
public interface FilaDeGeracao {

    void submeter(Runnable geracao);
}
