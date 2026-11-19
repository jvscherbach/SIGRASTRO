package br.gov.mt.sema.rastro.integration.siga;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Consulta trivial, sem tabela, pelo datasource somente leitura. */
@Component
@Order(1)
class VerificadorDoSiga implements VerificadorDeConexao {

    private static final Logger log = LoggerFactory.getLogger(VerificadorDoSiga.class);
    private static final int TEMPO_LIMITE_SEGUNDOS = 5;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    VerificadorDoSiga(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String nome() {
        return "siga";
    }

    @Override
    public String rotulo() {
        return "Banco do SIGA";
    }

    @Override
    public Estado estado() {
        return jdbc.getIfAvailable() == null ? Estado.NAO_CONFIGURADA : Estado.CONFIGURADA;
    }

    @Override
    public Resultado verificar() {
        try {
            jdbc.getObject().getJdbcOperations().execute((ConnectionCallback<Boolean>) conexao -> {
                try (PreparedStatement consulta = conexao.prepareStatement(SigaSql.carregar("verificacao"))) {
                    consulta.setQueryTimeout(TEMPO_LIMITE_SEGUNDOS);
                    try (ResultSet linhas = consulta.executeQuery()) {
                        return linhas.next();
                    }
                }
            });
            return new Resultado(true, "Conexão estabelecida.");
        } catch (RuntimeException e) {
            log.warn("Teste do banco do SIGA falhou: {}", e.getClass().getSimpleName());
            return new Resultado(false, "Banco do SIGA indisponível.");
        }
    }
}
