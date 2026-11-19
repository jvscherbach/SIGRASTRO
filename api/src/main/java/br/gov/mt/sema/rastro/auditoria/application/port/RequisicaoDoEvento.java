package br.gov.mt.sema.rastro.auditoria.application.port;

/** Dados da requisição corrente para o evento; {@code null} fora de uma requisição. */
public interface RequisicaoDoEvento {

    String origem();

    String correlationId();
}
