package br.gov.mt.sema.rastro.integration.siga;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Datasource somente leitura do SIGA (docs/padroes/connection-pooling.md). Fica em integration.siga porque só
 * este pacote pode usar javax.sql e Spring JDBC. Sem a URL, nada é criado e o adapter SigaNaoConfigurado responde 503.
 */
@Configuration(proxyBeanMethods = false)
class SigaDataSourceConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
    DataSource sigaDataSource(
            @Value("${rastro.datasource.siga.url}") String url,
            @Value("${rastro.datasource.siga.username:}") String usuario,
            @Value("${rastro.datasource.siga.password:}") String senha,
            @Value("${rastro.datasource.siga.hikari.maximum-pool-size:2}") int poolMaximo,
            @Value("${rastro.datasource.siga.hikari.connection-timeout:5000}") long timeoutConexaoMs,
            @Value("${rastro.datasource.siga.hikari.max-lifetime:1500000}") long vidaMaximaMs,
            @Value("${rastro.datasource.siga.hikari.read-timeout:30000}") long timeoutLeituraMs) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("siga-leitura");
        config.setJdbcUrl(url);
        config.setUsername(usuario);
        config.setPassword(senha);
        config.setReadOnly(true);
        config.setMaximumPoolSize(poolMaximo);
        // Fase inicial: nenhuma sessão ociosa no SIGA nem reconexão em segundo plano quando ele está fora do ar.
        config.setMinimumIdle(0);
        config.setConnectionTimeout(timeoutConexaoMs);
        config.setMaxLifetime(vidaMaximaMs);
        // A API sobe mesmo com o SIGA inacessível; as consultas respondem 503.
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("oracle.jdbc.ReadTimeout", String.valueOf(timeoutLeituraMs));
        return new HikariDataSource(config);
    }

    @Bean
    @ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
    NamedParameterJdbcTemplate sigaJdbcTemplate(DataSource sigaDataSource) {
        return new NamedParameterJdbcTemplate(sigaDataSource);
    }
}
