package br.gov.mt.sema.rastro.integration.siga;

import java.util.List;

import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento;
import br.gov.mt.sema.rastro.documento.domain.PapelDaPessoa;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Pessoas do requerimento para o SCORP (S4, docs/04): {@code documentos/pessoas-do-requerimento.sql}. O
 * {@code ID_CORPORATIVO} é lido como texto (o tipo no SIGA não muda o valor enviado ao SCORP). Falha de leitura segue a
 * tabela da S1 (503 ou 500), como as origens de documento.
 */
@Repository
@ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
class PessoasDoSigaJdbc implements PessoasDoRequerimento {

    private static final String CONSULTA = SigaSql.carregar("documentos/pessoas-do-requerimento");

    private final NamedParameterJdbcTemplate jdbc;

    PessoasDoSigaJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate) {
        this.jdbc = sigaJdbcTemplate;
    }

    @Override
    public List<Pessoa> listarPessoas(long idRequerimento) {
        return SigaFalhas.traduzir(() -> jdbc.query(CONSULTA, new MapSqlParameterSource("idRequerimento", idRequerimento),
                (rs, n) -> new Pessoa(rs.getString("ID_CORPORATIVO"), PapelDaPessoa.de(rs.getString("PAPEL")))));
    }
}
