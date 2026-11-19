package br.gov.mt.sema.rastro.integration.siga;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.function.Supplier;

import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/**
 * Traduz falhas de acesso ao SIGA. Falha de conexão, timeout e erros transitórios viram SIGA_INDISPONIVEL (503);
 * qualquer outro erro de dados (ex.: coluna inexistente, falta de grant) vira erro interno (500). O detalhe técnico
 * vai só para o log; nenhuma resposta traz SQL, tabela, URL ou mensagem do driver.
 */
final class SigaFalhas {

    private static final Logger log = LoggerFactory.getLogger(SigaFalhas.class);

    private SigaFalhas() {
    }

    static <T> T traduzir(Supplier<T> leitura) {
        try {
            return leitura.get();
        } catch (DataAccessException e) {
            if (indisponibilidade(e)) {
                log.warn("SIGA indisponível: {}", e.getMostSpecificCause().getMessage());
                throw new SigaIndisponivelException();
            }
            throw new IllegalStateException("Falha ao consultar o SIGA", e);
        }
    }

    private static boolean indisponibilidade(DataAccessException e) {
        if (e instanceof CannotGetJdbcConnectionException
                || e instanceof TransientDataAccessException
                || e instanceof DataAccessResourceFailureException) {
            return true;
        }
        for (Throwable causa = e.getCause(); causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLRecoverableException || causa instanceof SQLTransientException) {
                return true;
            }
        }
        return false;
    }
}
