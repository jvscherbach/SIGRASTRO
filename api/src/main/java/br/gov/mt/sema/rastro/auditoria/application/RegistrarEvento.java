package br.gov.mt.sema.rastro.auditoria.application;

import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;

/**
 * Contrato publicado pela auditoria aos demais módulos (somente inclusão). <strong>Nunca lança:</strong> falha de
 * auditoria não impede a operação de quem chamou.
 */
public interface RegistrarEvento {

    void registrar(NovoEvento evento);
}
