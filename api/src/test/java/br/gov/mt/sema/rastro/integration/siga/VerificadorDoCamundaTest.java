package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Map;

import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Classificacao;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Estado;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Resultado;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de conexão do Camunda (S7, bloco 10): lê uma tabela do Camunda sem trazer dado, com diagnóstico por causa.
 * Os testes com banco usam o Oracle de teste (pulados sem Docker); o diagnóstico é provado também sem Docker.
 */
class VerificadorDoCamundaTest {

    private static final ParametrosDoRastro PARAMETROS = new ParametrosDoRastro() {
        @Override
        public boolean mostrarNomeDoResponsavel() {
            return true;
        }

        @Override
        public int maximoDeNomesPorEquipe() {
            return 10;
        }

        @Override
        public int cacheDaPosicaoSegundos() {
            return 0;
        }

        @Override
        public int posicoesSimultaneas() {
            return 4;
        }

        @Override
        public int camundaTimeoutSegundos() {
            return 3;
        }
    };

    private static ObjectProvider<NamedParameterJdbcTemplate> provedor(NamedParameterJdbcTemplate jdbc) {
        StaticListableBeanFactory fabrica = jdbc == null ? new StaticListableBeanFactory()
                : new StaticListableBeanFactory(Map.of("sigaJdbcTemplate", jdbc));
        return fabrica.getBeanProvider(NamedParameterJdbcTemplate.class);
    }

    @Test
    void semSigaNaoConfigurado() {
        VerificadorDoCamunda verificador = new VerificadorDoCamunda(provedor(null), PARAMETROS);

        assertThat(verificador.nome()).isEqualTo("camunda");
        assertThat(verificador.estado()).isEqualTo(Estado.NAO_CONFIGURADA);
    }

    @Test
    void diagnosticoPorCausaSemMensagemCruaDoDriver() {
        String tempo = VerificadorDoCamunda.diagnostico(
                new DataAccessResourceFailureException("x", new SQLTimeoutException("detalhe do driver")), 3);
        String semGrant = VerificadorDoCamunda.diagnostico(
                new UncategorizedSQLException("t", "SELECT", new SQLException("ORA-00942 detalhe", "42000", 942)), 3);
        String outro = VerificadorDoCamunda.diagnostico(new IllegalStateException("host interno"), 3);

        assertThat(tempo).isEqualTo("Tempo limite das consultas ao fluxo (3 s) excedido.");
        assertThat(semGrant).contains("não tem acesso às tabelas do fluxo");
        assertThat(outro).isEqualTo("Banco do fluxo (Camunda) indisponível.");
        assertThat(tempo + semGrant + outro).doesNotContain("detalhe").doesNotContain("host interno")
                .doesNotContain("ORA-00942");
    }

    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class ComOracle {

        @Test
        void leituraComGrantEhOkSemTrazerDado() {
            try (HikariDataSource leitura = SigaOracleTeste.dataSourceLeitura()) {
                Resultado r = new VerificadorDoCamunda(provedor(new NamedParameterJdbcTemplate(leitura)), PARAMETROS)
                        .verificar();

                assertThat(r.classificacao()).isEqualTo(Classificacao.OK);
                assertThat(r.mensagem()).isEqualTo("Leitura das tabelas do fluxo estabelecida.");
            }
        }

        @Test
        void semGrantNoCamundaDizQueFaltaAcesso() {
            try (HikariDataSource semGrant = SigaOracleTeste.dataSource(SigaOracleTeste.SEM_PERMISSAO,
                    SigaOracleTeste.SENHA_SEM_PERMISSAO)) {
                Resultado r = new VerificadorDoCamunda(provedor(new NamedParameterJdbcTemplate(semGrant)), PARAMETROS)
                        .verificar();

                assertThat(r.classificacao()).isEqualTo(Classificacao.FALHA);
                assertThat(r.mensagem()).contains("não tem acesso às tabelas do fluxo");
            }
        }

        @Test
        void bancoForaDoArEhIndisponivel() {
            try (HikariDataSource fora = SigaOracleTeste.dataSourceInacessivel()) {
                Resultado r = new VerificadorDoCamunda(provedor(new NamedParameterJdbcTemplate(fora)), PARAMETROS)
                        .verificar();

                assertThat(r.classificacao()).isEqualTo(Classificacao.FALHA);
                assertThat(r.mensagem()).isEqualTo("Banco do fluxo (Camunda) indisponível.");
            }
        }
    }
}
