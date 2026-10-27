package br.gov.mt.sema.vistas.integration.siga;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Lê DATE e TIMESTAMP (sem fuso) do SIGA como relógio de parede de Cuiabá, sem passar por {@link java.sql.Timestamp}:
 * a conversão via {@code Timestamp} usa o fuso padrão da JVM e pode deslocar horários de borda (lacunas de horário de
 * verão). Um documento das 23:30 continua no mesmo dia, qualquer que seja o fuso do container.
 */
final class DataHoraDoSiga {

    private DataHoraDoSiga() {
    }

    static LocalDateTime ler(ResultSet rs, String coluna) throws SQLException {
        return rs.getObject(coluna, LocalDateTime.class);
    }
}
