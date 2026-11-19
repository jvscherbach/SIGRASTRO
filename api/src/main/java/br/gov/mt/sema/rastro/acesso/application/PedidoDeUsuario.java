package br.gov.mt.sema.rastro.acesso.application;

/**
 * O que a política de acesso precisa saber de um recurso criado por um usuário (pedido de dossiê), sem depender do
 * módulo dono dele.
 *
 * @param loginDoCriador       login de quem criou o pedido
 * @param cpfCnpjDoInteressado CPF/CNPJ do interessado do requerimento, como guardado no pedido (com ou sem máscara)
 * @param contemSigiloso       o pedido inclui documento sigiloso
 */
public record PedidoDeUsuario(String loginDoCriador, String cpfCnpjDoInteressado, boolean contemSigiloso) {

    public PedidoDeUsuario(String loginDoCriador, String cpfCnpjDoInteressado) {
        this(loginDoCriador, cpfCnpjDoInteressado, false);
    }
}
