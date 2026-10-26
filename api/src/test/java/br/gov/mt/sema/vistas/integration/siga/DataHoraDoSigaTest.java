package br.gov.mt.sema.vistas.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.TimeZone;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** TIMESTAMP e DATE do SIGA lidos como relógio de parede, mesmo com a JVM em UTC (23:30 de Cuiabá fica no mesmo dia). */
@Testcontainers(disabledWithoutDocker = true)
class DataHoraDoSigaTest {

    private static final String CONSULTA = "SELECT TIMESTAMP '2025-04-01 23:30:00' AS NOITE,"
            + " TIMESTAMP '2025-04-02 00:30:00' AS MADRUGADA,"
            + " TO_DATE('2025-04-01 23:30:00', 'YYYY-MM-DD HH24:MI:SS') AS DIA,"
            + " CAST(NULL AS TIMESTAMP) AS VAZIA FROM DUAL";

    private static TimeZone original;
    private static HikariDataSource dataSource;

    @BeforeAll
    static void abrir() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        dataSource = SigaOracleTeste.dataSourceLeitura();
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
        TimeZone.setDefault(original);
    }

    @Test
    void horariosDeBordaNaoDeslocamODia() {
        new JdbcTemplate(dataSource).query(CONSULTA, rs -> {
            assertThat(DataHoraDoSiga.ler(rs, "NOITE")).isEqualTo(LocalDateTime.of(2025, 4, 1, 23, 30));
            assertThat(DataHoraDoSiga.ler(rs, "MADRUGADA")).isEqualTo(LocalDateTime.of(2025, 4, 2, 0, 30));
            assertThat(DataHoraDoSiga.ler(rs, "DIA")).isEqualTo(LocalDateTime.of(2025, 4, 1, 23, 30));
            assertThat(DataHoraDoSiga.ler(rs, "VAZIA")).isNull();
        });
    }
}
