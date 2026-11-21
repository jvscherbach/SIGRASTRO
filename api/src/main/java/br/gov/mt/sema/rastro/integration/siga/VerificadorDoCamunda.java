package br.gov.mt.sema.rastro.integration.siga;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;

import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Teste de conexão do Camunda (S7, bloco 10), separado do banco do SIGA e da API de arquivos: lê uma linha de
 * {@code SIGA2_CAMUNDA.ACT_RE_PROCDEF} sem trazer dado (só a constante 1), pelo mesmo datasource somente leitura, com o
 * tempo limite das consultas ao fluxo ({@code RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS}, lido a cada teste). Diagnóstico por
 * causa: sem grant na tabela do Camunda, tempo limite ou banco indisponível; nunca a mensagem crua do driver.
 *
 * <p>É JDBC, sem HTTP: os falsos alarmes conhecidos das conexões HTTP (503 isolado, 400 à chave fictícia, 5 s de
 * espera) não se aplicam, e os testes da API de arquivos e do SCORP (S6) ficam como estão.
 */
@Component
@Order(5)
class VerificadorDoCamunda implements VerificadorDeConexao {

    private static final Logger log = LoggerFactory.getLogger(VerificadorDoCamunda.class);
    /** ORA-00942 (tabela ou visão inexistente para o usuário) e ORA-01031 (privilégio insuficiente). */
    private static final int SEM_TABELA = 942;
    private static final int SEM_PRIVILEGIO = 1031;
    /** ORA-01013: consulta cancelada pelo tempo limite. */
    private static final int CANCELADA = 1013;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ParametrosDoRastro parametros;

    VerificadorDoCamunda(ObjectProvider<NamedParameterJdbcTemplate> jdbc, ParametrosDoRastro parametros) {
        this.jdbc = jdbc;
        this.parametros = parametros;
    }

    @Override
    public String nome() {
        return "camunda";
    }

    @Override
    public String rotulo() {
        return "Fluxo (Camunda)";
    }

    @Override
    public Estado estado() {
        return jdbc.getIfAvailable() == null ? Estado.NAO_CONFIGURADA : Estado.CONFIGURADA;
    }

    @Override
    public Resultado verificar() {
        int tempoLimite = Math.max(1, parametros.camundaTimeoutSegundos());
        try {
            boolean leu = jdbc.getObject().getJdbcOperations().execute((ConnectionCallback<Boolean>) conexao -> {
                try (PreparedStatement consulta = conexao.prepareStatement(SigaSql.carregar("verificacao-camunda"))) {
                    consulta.setQueryTimeout(tempoLimite);
                    try (ResultSet linhas = consulta.executeQuery()) {
                        linhas.next();
                        return Boolean.TRUE;
                    }
                }
            });
            return new Resultado(leu, "Leitura das tabelas do fluxo estabelecida.");
        } catch (RuntimeException e) {
            log.warn("Teste do Camunda falhou: {}", e.getClass().getSimpleName());
            return Resultado.falha(diagnostico(e, tempoLimite));
        }
    }

    static String diagnostico(Throwable erro, int tempoLimite) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause() == causa ? null : causa.getCause()) {
            if (causa instanceof SQLTimeoutException
                    || (causa instanceof SQLException sql && sql.getErrorCode() == CANCELADA)) {
                return "Tempo limite das consultas ao fluxo (" + tempoLimite + " s) excedido.";
            }
            if (causa instanceof SQLException sql
                    && (sql.getErrorCode() == SEM_TABELA || sql.getErrorCode() == SEM_PRIVILEGIO)) {
                return "O banco respondeu, mas o usuário de leitura não tem acesso às tabelas do fluxo (Camunda). "
                        + "Peça ao DBA o SELECT no SIGA2_CAMUNDA.";
            }
        }
        return "Banco do fluxo (Camunda) indisponível.";
    }
}
