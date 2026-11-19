package br.gov.mt.sema.rastro.configuracao.application.port;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Optional;

import javax.net.ssl.SSLException;

/**
 * Estado e teste de uma conexão externa, implementado em {@code integration.*}. O destino vem só da configuração do
 * servidor: nada do pedido chega aqui. As mensagens não trazem URL, host, token nem detalhe de exceção.
 *
 * <p>S6, bloco 4: o resultado distingue <b>alcançável</b> de <b>funcional</b> ({@link Classificacao}). Uma resposta HTTP
 * de um serviço alcançável nunca é "falha de rede".
 */
public interface VerificadorDeConexao {

    enum Estado {
        CONFIGURADA,
        NAO_CONFIGURADA,
        DESATIVADA
    }

    /** Resultado do teste, com texto na tela (não só cor). */
    enum Classificacao {
        /** Resposta esperada de serviço funcional (2xx; no banco, a consulta de verificação). */
        OK,
        /** O serviço respondeu com um 4xx esperado para a chamada de teste (ex.: 400/404 para chave fictícia). */
        ALCANCAVEL_COM_RESSALVA,
        /** 401/403: a credencial foi recusada. */
        FALHA_DE_AUTENTICACAO,
        /** DNS, recusa, TLS, tempo esgotado ou 5xx persistente. */
        FALHA;

        /** O serviço respondeu (está no ar), ainda que com ressalva. */
        public boolean alcancavel() {
            return this == OK || this == ALCANCAVEL_COM_RESSALVA;
        }
    }

    /**
     * @param codigoHttp status da resposta do serviço, quando houve resposta HTTP; nulo nas falhas de rede e no banco
     */
    record Resultado(Classificacao classificacao, String mensagem, Integer codigoHttp) {

        /** Compatível com os verificadores sem HTTP (banco, taxas): sucesso é OK, o resto é FALHA. */
        public Resultado(boolean sucesso, String mensagem) {
            this(sucesso ? Classificacao.OK : Classificacao.FALHA, mensagem, null);
        }

        /** OK ou alcançável com ressalva. */
        public boolean sucesso() {
            return classificacao.alcancavel();
        }

        public static Resultado falha(String mensagem) {
            return new Resultado(Classificacao.FALHA, mensagem, null);
        }

        /**
         * Classifica a resposta HTTP de uma chamada de teste. 503 isolado é decidido por quem chama (uma nova
         * tentativa antes de concluir).
         *
         * @param chamadaDeTeste o que a chamada pede, para explicar o 4xx (ex.: "uma chave fictícia")
         * @param credencial     como a tela chama a credencial (ex.: "token")
         */
        public static Resultado deHttp(int status, String chamadaDeTeste, String credencial) {
            if (status >= 200 && status < 300) {
                return new Resultado(Classificacao.OK, "Serviço funcional: respondeu HTTP " + status + ".", status);
            }
            if (status == 401 || status == 403) {
                return new Resultado(Classificacao.FALHA_DE_AUTENTICACAO, "O serviço recusou o " + credencial
                        + " (HTTP " + status + "). Renove o " + credencial + ": capture um novo, atualize o arquivo e "
                        + "teste de novo.", status);
            }
            if (status >= 400 && status < 500) {
                return new Resultado(Classificacao.ALCANCAVEL_COM_RESSALVA, "Serviço alcançável: respondeu HTTP "
                        + status + " à chamada de teste, que usa " + chamadaDeTeste + ". É a resposta esperada; o "
                        + "consumo real usa valores verdadeiros.", status);
            }
            if (status >= 500) {
                return new Resultado(Classificacao.FALHA, "O serviço respondeu HTTP " + status + " (erro do próprio "
                        + "serviço).", status);
            }
            return new Resultado(Classificacao.FALHA, "Resposta inesperada do serviço (HTTP " + status + ").", status);
        }

        /** 503 de novo depois da nova tentativa. */
        public static Resultado indisponivelPersistente(int status) {
            return new Resultado(Classificacao.FALHA, "O serviço respondeu HTTP " + status + " duas vezes seguidas "
                    + "(indisponível). O consumo real tenta mais vezes; se persistir, o serviço está fora do ar.", status);
        }

        /** Falha sem resposta HTTP: o motivo pela causa (DNS, recusa, TLS, tempo), sem host nem mensagem crua. */
        public static Resultado deRede(Throwable erro, int tempoLimiteSegundos) {
            for (Throwable causa = erro; causa != null; causa = causa.getCause() == causa ? null : causa.getCause()) {
                if (causa instanceof UnknownHostException) {
                    return falha("Endereço do serviço não encontrado (DNS). Confira a URL configurada.");
                }
                if (causa instanceof SocketTimeoutException) {
                    return falha("Tempo limite do teste (" + tempoLimiteSegundos + " s) excedido sem resposta.");
                }
                if (causa instanceof SSLException) {
                    return falha("Falha na conexão segura (TLS/certificado).");
                }
                if (causa instanceof ConnectException || causa instanceof NoRouteToHostException) {
                    return falha("Conexão recusada ou sem rota até o serviço.");
                }
            }
            return falha("Serviço inacessível (falha de rede).");
        }
    }

    /** Identificador usado na rota. */
    String nome();

    String rotulo();

    Estado estado();

    /** Chamada leve, inofensiva, com o tempo limite do teste. Só é usada com o estado {@code CONFIGURADA}. */
    Resultado verificar();

    /** Horas até o token expirar, negativas se já venceu; vazio se não dá para saber. */
    default Optional<Long> horasAteExpirar() {
        return Optional.empty();
    }
}
