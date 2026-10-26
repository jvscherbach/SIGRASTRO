package br.gov.mt.sema.vistas.dossie.application.port;

import java.nio.file.Path;

/** Confere se o arquivo obtido é um PDF utilizável. Devolve o número de páginas ou lança {@link FalhaNoDocumentoException}. */
public interface ValidadorDePdf {

    int validar(Path arquivo);
}
