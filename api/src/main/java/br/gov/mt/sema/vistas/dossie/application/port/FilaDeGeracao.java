package br.gov.mt.sema.vistas.dossie.application.port;

/** Fila em memória com executor limitado a {@code DOSSIE_THREADS} gerações simultâneas. */
public interface FilaDeGeracao {

    void submeter(Runnable geracao);
}
