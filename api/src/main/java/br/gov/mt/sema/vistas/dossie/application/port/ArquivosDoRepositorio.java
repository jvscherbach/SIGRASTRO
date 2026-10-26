package br.gov.mt.sema.vistas.dossie.application.port;

import java.nio.file.Path;

/**
 * Obtém o binário de um arquivo do repositório e o grava em disco (RNF03), implementada por integration.licenciamento.
 * Falha do documento: {@link FalhaNoDocumentoException}. Credencial recusada (401/403): CredencialArquivosRecusadaException,
 * que interrompe toda a geração. Em qualquer falha o arquivo parcial é apagado.
 */
public interface ArquivosDoRepositorio {

    void baixar(String chave, Path destino);
}
