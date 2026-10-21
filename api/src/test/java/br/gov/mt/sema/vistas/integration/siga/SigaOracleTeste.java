package br.gov.mt.sema.vistas.integration.siga;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Oracle 23 Free compartilhado por todas as classes de teste de integração (sobe uma vez por execução do Maven),
 * com o recorte do modelo do SIGA de src/test/resources/sql/siga. Só é tocado por classes anotadas com
 * {@code @Testcontainers(disabledWithoutDocker = true)}.
 *
 * Atenção: isto prova o SQL no Oracle 23 Free, não no 19c. A compatibilidade com o 19c vem da lista de recursos
 * proibidos (docs/padroes/persistencia-e-migrations.md) e da homologação no SIGA real.
 */
final class SigaOracleTeste {

    static final String LEITURA = "VISTAS_LEITURA_SIGA";
    static final String SENHA_LEITURA = "leitura_teste";
    static final String SEM_PERMISSAO = "SEM_PERMISSAO";
    static final String SENHA_SEM_PERMISSAO = "sem_permissao_teste";

    private static final OracleContainer ORACLE = new OracleContainer(
            DockerImageName.parse("gvenzl/oracle-free:23.26.3-slim-faststart"))
            .withStartupTimeout(Duration.ofMinutes(8));

    static {
        ORACLE.start();
        try (Connection conexao = DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword())) {
            for (String script : new String[] {"01-recorte.sql", "02-dados.sql", "03-dados-s11.sql"}) {
                ScriptUtils.executeSqlScript(conexao,
                        new EncodedResource(new ClassPathResource("sql/siga/" + script), "UTF-8"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao preparar o recorte do SIGA para teste", e);
        }
    }

    private SigaOracleTeste() {
    }

    static HikariDataSource dataSource(String usuario, String senha) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(ORACLE.getJdbcUrl());
        config.setUsername(usuario);
        config.setPassword(senha);
        config.setReadOnly(true);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        return new HikariDataSource(config);
    }

    static HikariDataSource dataSourceLeitura() {
        return dataSource(LEITURA, SENHA_LEITURA);
    }

    /** Datasource que aponta para uma porta fechada: simula o SIGA fora do ar sem esperar timeout longo. */
    static HikariDataSource dataSourceInacessivel() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:oracle:thin:@//127.0.0.1:1/INEXISTENTE");
        config.setUsername("x");
        config.setPassword("x");
        config.setConnectionTimeout(1000);
        config.setInitializationFailTimeout(-1);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(1);
        return new HikariDataSource(config);
    }
}
