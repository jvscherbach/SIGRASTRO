package br.gov.mt.sema.vistas.auditoria.application;

import java.util.stream.Stream;

/**
 * CSV pronto para ser escrito na resposta: as linhas são geradas sob demanda.
 *
 * @param truncado havia mais linhas do que o limite
 */
public record ExportacaoDeEventos(String nomeDoArquivo, boolean truncado, String cabecalho, Stream<String> linhas) {
}
