package br.gov.mt.sema.vistas.dossie.domain;

import br.gov.mt.sema.vistas.auditoria.domain.UsuarioDoEvento;

/**
 * Retrato de quem pediu e da requisição que criou o pedido: a geração roda em outra thread, sem usuário na requisição,
 * e os eventos {@code DOSSIE} e {@code FALHA} saem daqui.
 */
public record AutoriaDoPedido(UsuarioDoEvento usuario, String numeroProcesso, String numeroRequerimento, String origem,
        String correlationId) {
}
