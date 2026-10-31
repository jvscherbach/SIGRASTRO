package br.gov.mt.sema.vistas.configuracao.api.dto;

import java.util.List;

import br.gov.mt.sema.vistas.configuracao.application.GerirConexoes.SituacaoDaConexao;
import br.gov.mt.sema.vistas.configuracao.application.GerirConexoes.TesteDaConexao;

/** Nenhum campo traz URL, host ou credencial. */
public record ConexoesResponse(List<Conexao> conexoes) {

    /** @param horasAteExpirar nulo se não dá para saber; 0 com o token vencido */
    public record Conexao(String nome, String rotulo, String estado, Long horasAteExpirar, boolean tokenVencido) {

        static Conexao de(SituacaoDaConexao situacao) {
            return new Conexao(situacao.nome(), situacao.rotulo(), situacao.estado().name(),
                    limitar(situacao.horasAteExpirar()), vencido(situacao.horasAteExpirar()));
        }
    }

    public record Teste(String nome, boolean sucesso, long tempoMs, String mensagem, Long horasAteExpirar,
            boolean tokenVencido) {

        public static Teste de(TesteDaConexao teste) {
            return new Teste(teste.nome(), teste.sucesso(), teste.tempoMs(), teste.mensagem(),
                    limitar(teste.horasAteExpirar()), vencido(teste.horasAteExpirar()));
        }
    }

    public static ConexoesResponse de(List<SituacaoDaConexao> situacoes) {
        return new ConexoesResponse(situacoes.stream().map(Conexao::de).toList());
    }

    private static Long limitar(Long horas) {
        return horas == null ? null : Long.valueOf(Math.max(0, horas));
    }

    private static boolean vencido(Long horas) {
        return horas != null && horas <= 0;
    }
}
