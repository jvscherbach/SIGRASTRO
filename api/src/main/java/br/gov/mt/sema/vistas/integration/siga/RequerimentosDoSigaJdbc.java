package br.gov.mt.sema.vistas.integration.siga;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.vistas.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.vistas.consulta.domain.CriterioBusca;
import br.gov.mt.sema.vistas.consulta.domain.Interessado;
import br.gov.mt.sema.vistas.consulta.domain.NumeroRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.Pagina;
import br.gov.mt.sema.vistas.consulta.domain.Requerimento;
import br.gov.mt.sema.vistas.consulta.domain.ResumoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.Situacao;
import br.gov.mt.sema.vistas.consulta.domain.TipoBusca;
import br.gov.mt.sema.vistas.integration.siga.dto.RequerimentoLinha;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Leitura de requerimentos no SIGA (somente SELECT, parâmetros nomeados). Só a tabela REQUERIMENTO importa: a
 * tabela PROCESSO não é consultada e a origem do requerimento não filtra nada (docs/04, P03).
 */
@Repository
@ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
class RequerimentosDoSigaJdbc implements RequerimentosDoSiga {

    private static final Logger log = LoggerFactory.getLogger(RequerimentosDoSigaJdbc.class);

    private static final String MARCADOR_FILTROS = "/*FILTROS*/";
    private static final String FILTRO_NUMERO_REQUERIMENTO = "\n  AND r.NUMERO = :numero";
    private static final String FILTRO_NUMERO_PROCESSO = "\n  AND r.NUMERO_PROCESSO = :numero";
    // PESSOA.CPF_CNPJ guarda só dígitos (11 ou 14): comparação direta, que permite usar o índice.
    private static final String FILTRO_CPF_CNPJ = "\n  AND p.CPF_CNPJ = :cpfCnpj";
    private static final String FILTRO_NOME = "\n  AND UPPER(p.NOME) LIKE :nome ESCAPE '\\'";
    private static final String FILTRO_RESTRICAO = "\n  AND p.CPF_CNPJ = :restricao";

    private final NamedParameterJdbcTemplate jdbc;
    private final String sqlPorNumero = SigaSql.carregar("requerimento-por-numero");
    private final String sqlPorId = SigaSql.carregar("requerimento-por-id");
    private final String sqlLista = SigaSql.carregar("requerimentos-por-interessado");
    private final String sqlContagem = SigaSql.carregar("requerimentos-contagem");

    RequerimentosDoSigaJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate) {
        this.jdbc = sigaJdbcTemplate;
    }

    @Override
    public Optional<Requerimento> buscarPorNumeroDeRequerimento(NumeroRequerimento numero) {
        return umRequerimento(sqlPorNumero, new MapSqlParameterSource("numero", numero.texto()));
    }

    @Override
    public Optional<Requerimento> buscarPorId(long id) {
        return umRequerimento(sqlPorId, new MapSqlParameterSource("id", id));
    }

    @Override
    public Pagina<ResumoRequerimento> buscarPorCriterios(CriterioBusca criterio) {
        MapSqlParameterSource filtros = new MapSqlParameterSource();
        String where = montarFiltros(criterio, filtros);
        return SigaFalhas.traduzir(() -> {
            Long total = jdbc.queryForObject(sqlContagem.replace(MARCADOR_FILTROS, where), filtros, Long.class);
            if (total == null || total == 0) {
                return Pagina.vazia(criterio.pagina(), criterio.tamanho());
            }
            MapSqlParameterSource pagina = new MapSqlParameterSource(filtros.getValues())
                    .addValue("inicio", criterio.inicio())
                    .addValue("tamanho", criterio.tamanho());
            List<ResumoRequerimento> itens = jdbc.query(sqlLista.replace(MARCADOR_FILTROS, where), pagina,
                    LINHA_DA_LISTA).stream().map(RequerimentosDoSigaJdbc::paraResumo).toList();
            return new Pagina<>(itens, total, criterio.pagina(), criterio.tamanho());
        });
    }

    /** Monta só predicados fixos desta classe; a entrada do usuário vai sempre como parâmetro nomeado. */
    private static String montarFiltros(CriterioBusca criterio, MapSqlParameterSource parametros) {
        StringBuilder where = new StringBuilder();
        if (criterio.numero() != null) {
            where.append(criterio.tipo() == TipoBusca.PROCESSO ? FILTRO_NUMERO_PROCESSO : FILTRO_NUMERO_REQUERIMENTO);
            parametros.addValue("numero", criterio.numero().texto());
        }
        if (criterio.cpfCnpj() != null) {
            where.append(FILTRO_CPF_CNPJ);
            parametros.addValue("cpfCnpj", criterio.cpfCnpj().digitos());
        }
        if (criterio.nome() != null) {
            where.append(FILTRO_NOME);
            parametros.addValue("nome", criterio.nomeComoPadraoLike());
        }
        if (criterio.restricaoDigitos() != null) {
            where.append(FILTRO_RESTRICAO);
            parametros.addValue("restricao", criterio.restricaoDigitos());
        }
        return where.toString();
    }

    private Optional<Requerimento> umRequerimento(String sql, MapSqlParameterSource parametros) {
        List<RequerimentoLinha> linhas = SigaFalhas.traduzir(() -> jdbc.query(sql, parametros, LINHA_DO_DETALHE));
        if (linhas.isEmpty()) {
            return Optional.empty();
        }
        if (linhas.size() > 1) {
            log.warn("Mais de um requerimento para o mesmo critério; usando o mais recente (id {})", linhas.get(0).id());
        }
        return Optional.of(paraRequerimento(linhas.get(0)));
    }

    private static Requerimento paraRequerimento(RequerimentoLinha l) {
        return new Requerimento(l.id(), l.numero(), l.numeroProcesso(), l.tipoRequerimento(),
                Situacao.de(l.situacao()), l.dataAbertura(), l.dataAlteracao(), interessado(l), l.tipoProcesso());
    }

    private static ResumoRequerimento paraResumo(RequerimentoLinha l) {
        return new ResumoRequerimento(l.id(), l.numero(), l.numeroProcesso(), interessado(l), l.tipoProcesso(),
                Situacao.de(l.situacao()), l.dataAbertura());
    }

    private static Interessado interessado(RequerimentoLinha l) {
        boolean semInteressado = l.nomeInteressado() == null && l.cpfCnpjInteressado() == null;
        return semInteressado ? null : new Interessado(l.nomeInteressado(), l.cpfCnpjInteressado());
    }

    private static final RowMapper<RequerimentoLinha> LINHA_DA_LISTA = (rs, n) -> linha(rs, null);

    private static final RowMapper<RequerimentoLinha> LINHA_DO_DETALHE = (rs, n) ->
            linha(rs, rs.getString("TIPO_REQUERIMENTO"));

    private static RequerimentoLinha linha(ResultSet rs, String tipoRequerimento) throws SQLException {
        return new RequerimentoLinha(rs.getLong("ID"), rs.getString("NUMERO"), rs.getString("NUMERO_PROCESSO"),
                data(rs, "DATA_ABERTURA"), data(rs, "DATA_ALTERACAO"), rs.getString("SITUACAO_PROCESSO"),
                rs.getString("TIPO_PROCESSO"), tipoRequerimento, rs.getString("NOME_INTERESSADO"),
                rs.getString("CPF_CNPJ"));
    }

    private static LocalDateTime data(ResultSet rs, String coluna) throws SQLException {
        return DataHoraDoSiga.ler(rs, coluna);
    }
}
