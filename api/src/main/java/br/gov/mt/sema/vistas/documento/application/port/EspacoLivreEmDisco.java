package br.gov.mt.sema.vistas.documento.application.port;

import java.nio.file.Path;

/** Espaço livre (utilizável) do sistema de arquivos do diretório temporário das geometrias. */
public interface EspacoLivreEmDisco {

    long livreEmBytes(Path diretorio);
}
