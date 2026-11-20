package br.gov.mt.sema.rastro.configuracao.api.dto;

import java.util.List;

import br.gov.mt.sema.rastro.configuracao.application.GerirConexoes.Conexoes;
import br.gov.mt.sema.rastro.configuracao.application.GerirConexoes.SituacaoDaConexao;
import br.gov.mt.sema.rastro.configuracao.application.GerirConexoes.TesteDaConexao;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Nenhum campo traz URL, host, credencial ou corpo do serviço. {@code tempoLimiteDoTesteSegundos} (S6) é o tempo limite
 * de cada chamada do teste ({@code CONEXAO_TESTE_TIMEOUT_SEGUNDOS}).
 */
public record ConexoesResponse(List<Conexao> conexoes, int tempoLimiteDoTesteSegundos) {

    /** @param horasAteExpirar nulo se não dá para saber; 0 com o token vencido */
    public record Conexao(String nome, String rotulo, String estado, Long horasAteExpirar, boolean tokenVencido) {

        static Conexao de(SituacaoDaConexao situacao) {
            return new Conexao(situacao.nome(), situacao.rotulo(), situacao.estado().name(),
                    limitar(situacao.horasAteExpirar()), vencido(situacao.horasAteExpirar()));
        }
    }

    /**
     * @param sucesso       o serviço respondeu (OK ou ALCANCAVEL_COM_RESSALVA)
     * @param classificacao OK, ALCANCAVEL_COM_RESSALVA, FALHA_DE_AUTENTICACAO ou FALHA (S6)
     * @param codigoHttp    status da resposta do serviço; ausente sem resposta HTTP
     */
    public record Teste(String nome, boolean sucesso, String classificacao,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer codigoHttp, long tempoMs, int tempoLimiteSegundos,
            String mensagem, Long horasAteExpirar, boolean tokenVencido) {

        public static Teste de(TesteDaConexao teste) {
            return new Teste(teste.nome(), teste.sucesso(), teste.classificacao().name(), teste.codigoHttp(),
                    teste.tempoMs(), teste.tempoLimiteSegundos(), teste.mensagem(), limitar(teste.horasAteExpirar()),
                    vencido(teste.horasAteExpirar()));
        }
    }

    public static ConexoesResponse de(Conexoes conexoes) {
        return new ConexoesResponse(conexoes.conexoes().stream().map(Conexao::de).toList(),
                conexoes.tempoLimiteDoTesteSegundos());
    }

    private static Long limitar(Long horas) {
        return horas == null ? null : Long.valueOf(Math.max(0, horas));
    }

    private static boolean vencido(Long horas) {
        return horas != null && horas <= 0;
    }
}
