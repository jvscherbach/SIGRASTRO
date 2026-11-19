package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.sql.SQLRecoverableException;
import java.sql.SQLSyntaxErrorException;

import javax.sql.DataSource;

import br.gov.mt.sema.rastro.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.rastro.consulta.application.TarefaAtualDoRequerimento;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SigaConfiguracaoTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(SigaDataSourceConfig.class, SigaNaoConfigurado.class,
                    RequerimentosDoSigaJdbc.class, TarefaAtualDoSigaJdbc.class);

    @Test
    void urlAusenteNaoCriaDataSourceEUsaOAdapterNaoConfigurado() {
        contexto.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(DataSource.class);
            assertThat(ctx).doesNotHaveBean(NamedParameterJdbcTemplate.class);
            assertThat(ctx).hasSingleBean(SigaNaoConfigurado.class);
            assertThat(ctx).hasSingleBean(RequerimentosDoSiga.class);
            assertThat(ctx).hasSingleBean(TarefaAtualDoRequerimento.class);
            assertThatThrownBy(() -> ctx.getBean(RequerimentosDoSiga.class).buscarPorId(1))
                    .isInstanceOf(SigaIndisponivelException.class);
        });
    }

    @Test
    void urlVaziaOuEmBrancoTambemNaoCriaDataSource() {
        contexto.withPropertyValues("rastro.datasource.siga.url=").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(DataSource.class);
            assertThat(ctx).hasSingleBean(SigaNaoConfigurado.class);
        });
        contexto.withPropertyValues("rastro.datasource.siga.url=   ").run(ctx ->
                assertThat(ctx).doesNotHaveBean(DataSource.class));
    }

    @Test
    void urlPreenchidaCriaDataSourceSomenteLeituraComPoolPequenoESemSessaoOciosa() {
        contexto.withPropertyValues(
                "rastro.datasource.siga.url=jdbc:oracle:thin:@//127.0.0.1:1/INEXISTENTE",
                "rastro.datasource.siga.username=leitura",
                "rastro.datasource.siga.password=nao-usada").run(ctx -> {
            assertThat(ctx).hasSingleBean(DataSource.class);
            assertThat(ctx).hasSingleBean(NamedParameterJdbcTemplate.class);
            assertThat(ctx).doesNotHaveBean(SigaNaoConfigurado.class);
            assertThat(ctx).hasSingleBean(RequerimentosDoSigaJdbc.class);
            HikariDataSource hikari = (HikariDataSource) ctx.getBean(DataSource.class);
            assertThat(hikari.isReadOnly()).isTrue();
            assertThat(hikari.getMaximumPoolSize()).isEqualTo(2);
            assertThat(hikari.getMinimumIdle()).isZero();
            assertThat(hikari.getInitializationFailTimeout()).isEqualTo(-1);
            assertThat(hikari.getConnectionTimeout()).isEqualTo(5000);
        });
    }

    @Test
    void traducaoDeFalhasDeConexaoETimeoutViraIndisponivel() {
        assertThatThrownBy(() -> SigaFalhas.traduzir(() -> {
            throw new CannotGetJdbcConnectionException("x", new java.sql.SQLException("ORA-12541"));
        })).isInstanceOf(SigaIndisponivelException.class);
        assertThatThrownBy(() -> SigaFalhas.traduzir(() -> {
            throw new QueryTimeoutException("x");
        })).isInstanceOf(SigaIndisponivelException.class);
        assertThatThrownBy(() -> SigaFalhas.traduzir(() -> {
            throw new DataAccessResourceFailureException("x", new ConnectException());
        })).isInstanceOf(SigaIndisponivelException.class);
        assertThatThrownBy(() -> SigaFalhas.traduzir(() -> {
            throw new UncategorizedSQLException("t", "sql", new SQLRecoverableException("conexão perdida"));
        })).isInstanceOf(SigaIndisponivelException.class);
    }

    @Test
    void colunaOuTabelaInexistenteViraErroInternoSemDetalheTecnico() {
        assertThatThrownBy(() -> SigaFalhas.traduzir(() -> {
            throw new BadSqlGrammarException("t", "SELECT X FROM SIGA2_LICENCIAMENTO.TABELA",
                    new SQLSyntaxErrorException("ORA-00942"));
        })).isInstanceOf(IllegalStateException.class).hasMessage("Falha ao consultar o SIGA");
    }

    @Test
    void resultadoSemFalhaPassaDireto() {
        assertThat(SigaFalhas.traduzir(() -> 42)).isEqualTo(42);
    }
}
