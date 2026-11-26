package br.gov.mt.sema.rastro.integration.siga;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;

import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
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

    /** Cenários fictícios da S7 (Camunda e Licenciamento), gravados ao subir; ids em {@link CenariosFicticiosDaS7}. */
    static final Map<String, CamundaFicticioBuilder.Resultado> CENARIOS_S7;

    /** Cenários fictícios do caminho percorrido da S8 (HC, LC, PB), ids 8001 a 8199 em {@link CenariosFicticiosDaS8}. */
    static final Map<String, CamundaFicticioBuilder.Resultado> CENARIOS_S8;

    /** Cenários fictícios do financeiro da S9 (FIN01 a FIN16 e os de apoio), ids 9001 a 9199 em {@link CenariosFicticiosDaS9}. */
    static final Map<String, CamundaFicticioBuilder.Resultado> CENARIOS_S9;

    static {
        ORACLE.start();
        try (Connection conexao = DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword())) {
            for (String script : new String[] {"01-recorte.sql", "02-dados.sql", "03-dados-s11.sql", "04-documentos-s2.sql",
                    "05-pessoas-s4.sql", "06-camunda-s7.sql", "07-historico-s8.sql", "08-financeiro-s9.sql"}) {
                ScriptUtils.executeSqlScript(conexao,
                        new EncodedResource(new ClassPathResource("sql/siga/" + script), "UTF-8"));
            }
            CamundaFicticioBuilder construtor = construtorDeCenarios(conexao);
            CENARIOS_S7 = Map.copyOf(CenariosFicticiosDaS7.carregar(construtor));
            CENARIOS_S8 = Map.copyOf(CenariosFicticiosDaS8.carregar(construtor));
            CENARIOS_S9 = Map.copyOf(CenariosFicticiosDaS9.carregar(construtor));
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao preparar o recorte do SIGA para teste", e);
        }
    }

    private static Map<String, CamundaFicticioBuilder.Resultado> cenariosDoPainel;

    /**
     * População fictícia do painel (S10, PN01 a PN16, ids 10001 a 12999, {@link CenariosFicticiosDaS10}). Gravada sob
     * demanda, uma única vez por execução: o volume (1.200 linhas) só interessa aos testes do painel.
     */
    static synchronized Map<String, CamundaFicticioBuilder.Resultado> cenariosDoPainel() {
        if (cenariosDoPainel == null) {
            try (Connection conexao = DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword())) {
                cenariosDoPainel = Map.copyOf(CenariosFicticiosDaS10.carregar(construtorDeCenarios(conexao)));
            } catch (SQLException e) {
                throw new IllegalStateException("Falha ao gravar a população do painel para teste", e);
            }
        }
        return cenariosDoPainel;
    }

    private static Map<String, CamundaFicticioBuilder.Resultado> cenariosDaCaixa;
    private static Map<String, CamundaFicticioBuilder.Resultado> volumeDaCaixa;

    /**
     * População fictícia da caixa do analista (S11, CX01 a CX17, ids 15001 a 15017 e pessoas 15001 a 15004,
     * {@link CenariosFicticiosDaS11}). Gravada sob demanda, uma única vez por execução. Datas em 2027 e 2028: fora da base do
     * painel de 2026.
     */
    static synchronized Map<String, CamundaFicticioBuilder.Resultado> cenariosDaCaixa() {
        if (cenariosDaCaixa == null) {
            try (Connection conexao = DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword())) {
                cenariosDaCaixa = Map.copyOf(CenariosFicticiosDaS11.carregar(construtorDeCenarios(conexao)));
            } catch (SQLException e) {
                throw new IllegalStateException("Falha ao gravar a população da caixa para teste", e);
            }
        }
        return cenariosDaCaixa;
    }

    /** Volume da caixa (S11): 1.200 requerimentos na análise (ids 16001 a 17200), sob demanda e separado dos cenários. */
    static synchronized Map<String, CamundaFicticioBuilder.Resultado> volumeDaCaixa() {
        if (volumeDaCaixa == null) {
            try (Connection conexao = DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword())) {
                volumeDaCaixa = Map.copyOf(CenariosFicticiosDaS11.carregarVolume(construtorDeCenarios(conexao)));
            } catch (SQLException e) {
                throw new IllegalStateException("Falha ao gravar o volume da caixa para teste", e);
            }
        }
        return volumeDaCaixa;
    }

    private static CamundaFicticioBuilder construtorDeCenarios(Connection conexao) {
        return new CamundaFicticioBuilder(
                new NamedParameterJdbcTemplate(new SingleConnectionDataSource(conexao, true)),
                CatalogoDeEtapasDeTeste.padrao());
    }

    /**
     * Construtor de cenários com escrita (usuário {@code system}), para o teste que precisa de um cenário próprio além dos
     * da S7 e da S8. Use ids fora das faixas já ocupadas (7000 a 7299 são da S7 e dos testes dela; 8000 a 8199 são da S8;
     * os testes da S8 usam a partir de 8200; 9001 a 9199 são da S9; 10001 a 12999 são da população do painel da S10,
     * a partir de 13000 são dos testes da S10, 15001 a 15099 e 16001 a 17200 são da população da caixa da S11, a partir de
     * 18000 são dos testes da S11). Feche o datasource ao terminar.
     */
    static HikariDataSource dataSourceAdministrador() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(ORACLE.getJdbcUrl());
        config.setUsername("system");
        config.setPassword(ORACLE.getPassword());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        return new HikariDataSource(config);
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

    /** URL do Oracle de teste, para um contexto Spring apontar o datasource do SIGA para ele (S7, bloco 7). */
    static String urlJdbc() {
        return ORACLE.getJdbcUrl();
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
