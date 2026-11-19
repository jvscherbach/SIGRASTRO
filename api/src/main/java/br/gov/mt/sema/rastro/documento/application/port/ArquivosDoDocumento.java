package br.gov.mt.sema.rastro.documento.application.port;

import java.nio.file.Path;

import br.gov.mt.sema.rastro.documento.domain.Documento;

/**
 * Obtém o binário de um documento para a visualização (S3), implementada por integration.licenciamento com o mesmo
 * cliente do dossiê (mesmos tempos e novas tentativas). Recebe o documento, não a chave: a chave só é lida no adapter
 * (RNF09). O corpo vai direto para {@code destino}, nunca para a memória; em qualquer falha nada fica no destino.
 *
 * @throws FalhaAoObterArquivoException com o motivo tipado
 * @throws br.gov.mt.sema.rastro.shared.application.exception.CredencialArquivosRecusadaException token recusado
 */
public interface ArquivosDoDocumento {

    void baixar(Documento documento, Path destino);
}
