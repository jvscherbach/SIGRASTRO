package br.gov.mt.sema.rastro.config;

import br.gov.mt.sema.rastro.acesso.application.ParametrosDoLogin;
import br.gov.mt.sema.rastro.acesso.domain.HashDeSenha;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Parâmetros do login (L1) vindos do ambiente ({@code rastro.login.*}, variáveis {@code RASTRO_LOGIN_*}); valor fora da
 * faixa cai no padrão, com aviso no log (só o nome da variável). Todos são de classe "reinício": são lidos uma vez, na
 * subida. Também cria o {@link HashDeSenha} com o custo configurado.
 */
@Configuration
class ParametrosDoLoginDoAmbiente implements ParametrosDoLogin {

    private static final Logger log = LoggerFactory.getLogger(ParametrosDoLoginDoAmbiente.class);

    private final int iteracoes;
    private final int tentativas;
    private final int janelaMinutos;
    private final int bloqueioMinutos;
    private final int limitePorOrigem;

    ParametrosDoLoginDoAmbiente(
            @Value("${rastro.login.iteracoes:600000}") int iteracoes,
            @Value("${rastro.login.tentativas:3}") int tentativas,
            @Value("${rastro.login.janela-minutos:15}") int janelaMinutos,
            @Value("${rastro.login.bloqueio-minutos:15}") int bloqueioMinutos,
            @Value("${rastro.login.limite-por-origem:10}") int limitePorOrigem) {
        this.iteracoes = faixa("RASTRO_LOGIN_ITERACOES", iteracoes, 100_000, 2_000_000, 600_000);
        this.tentativas = faixa("RASTRO_LOGIN_TENTATIVAS", tentativas, 3, 10, 3);
        this.janelaMinutos = faixa("RASTRO_LOGIN_JANELA_MINUTOS", janelaMinutos, 1, 120, 15);
        this.bloqueioMinutos = faixa("RASTRO_LOGIN_BLOQUEIO_MINUTOS", bloqueioMinutos, 1, 120, 15);
        this.limitePorOrigem = faixa("RASTRO_LOGIN_LIMITE_POR_ORIGEM", limitePorOrigem, 5, 100, 10);
    }

    @Bean
    HashDeSenha hashDeSenha() {
        return new HashDeSenha(iteracoes);
    }

    private static int faixa(String variavel, int valor, int minimo, int maximo, int padrao) {
        if (valor >= minimo && valor <= maximo) {
            return valor;
        }
        log.warn("{} fora da faixa; usando {}", variavel, padrao);
        return padrao;
    }

    @Override
    public int iteracoes() {
        return iteracoes;
    }

    @Override
    public int tentativas() {
        return tentativas;
    }

    @Override
    public int janelaMinutos() {
        return janelaMinutos;
    }

    @Override
    public int bloqueioMinutos() {
        return bloqueioMinutos;
    }

    @Override
    public int limitePorOrigem() {
        return limitePorOrigem;
    }
}
