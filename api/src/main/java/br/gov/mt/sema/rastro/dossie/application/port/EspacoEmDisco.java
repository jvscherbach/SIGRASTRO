package br.gov.mt.sema.rastro.dossie.application.port;

import java.nio.file.Path;

/** Espaço livre (utilizável) do sistema de arquivos onde fica o diretório do pedido. */
public interface EspacoEmDisco {

    long livreEmBytes(Path diretorio);
}
