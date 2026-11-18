package br.gov.mt.sema.vistas.configuracao.application.port;

import java.util.Optional;

/**
 * Estado e teste de uma conexão externa, implementado em {@code integration.*}. O destino vem só da configuração do
 * servidor: nada do pedido chega aqui. As mensagens não trazem URL, host, token nem detalhe de exceção.
 */
public interface VerificadorDeConexao {

    enum Estado {
        CONFIGURADA,
        NAO_CONFIGURADA,
        DESATIVADA
    }

    record Resultado(boolean sucesso, String mensagem) {
    }

    /** Identificador usado na rota. */
    String nome();

    String rotulo();

    Estado estado();

    /** Chamada leve, com tempo limite curto. Só é usada com o estado {@code CONFIGURADA}. */
    Resultado verificar();

    /** Horas até o token expirar, negativas se já venceu; vazio se não dá para saber. */
    default Optional<Long> horasAteExpirar() {
        return Optional.empty();
    }
}
