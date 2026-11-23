package br.gov.mt.sema.rastro.integration.siga;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.application.port.ParametrosDoFinanceiro;
import br.gov.mt.sema.rastro.financeiro.domain.Lancamento;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Único componente que lê {@code SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA} (S9, bloco 2; risco R04). Uma só consulta
 * ({@code sql/siga/financeiro/lancamentos.sql}) para um requerimento e para o lote: só {@code SELECT}, parâmetros nomeados,
 * limite por requerimento, {@code FETCH FIRST} total e tempo limite por consulta ({@code RASTRO_FINANCEIRO_TIMEOUT_SEGUNDOS},
 * lido a cada consulta). Não interpreta nada; devolve os fatos crus.
 *
 * <p>Consultas: <b>1</b> por requerimento; no lote, <b>1</b> a cada {@value LancamentosDoRequerimento#MAXIMO_POR_LOTE}
 * requerimentos distintos. Nenhum laço por linha.
 */
@Repository
@ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
class LancamentosDoSigaJdbc implements LancamentosDoRequerimento {

    private final NamedParameterJdbcTemplate jdbc;
    private final IntSupplier limite;
    private final String sql = SigaSql.carregar("financeiro/lancamentos");

    @Autowired
    LancamentosDoSigaJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate, ParametrosDoFinanceiro parametros) {
        this(sigaJdbcTemplate, parametros::consultaTimeoutSegundos, parametros::limiteDeLancamentos);
    }

    /** Para teste: tempo limite e limite por requerimento fixos. */
    LancamentosDoSigaJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate, int timeoutSegundos, int limiteDeLancamentos) {
        this(sigaJdbcTemplate, () -> timeoutSegundos, () -> limiteDeLancamentos);
    }

    private LancamentosDoSigaJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate, IntSupplier timeoutSegundos,
            IntSupplier limite) {
        this.limite = limite;
        // Template próprio: o tempo limite vale só para o financeiro (como na tramitação), lido de novo a cada consulta.
        JdbcTemplate base = new JdbcTemplate(sigaJdbcTemplate.getJdbcTemplate().getDataSource()) {
            @Override
            public int getQueryTimeout() {
                return Math.max(1, timeoutSegundos.getAsInt());
            }
        };
        this.jdbc = new NamedParameterJdbcTemplate(base);
    }

    @Override
    public LeituraDosLancamentos lerDoRequerimento(long idRequerimento) {
        return ler(List.of(idRequerimento)).getOrDefault(idRequerimento, LeituraDosLancamentos.NENHUM);
    }

    @Override
    public Map<Long, LeituraDosLancamentos> lerEmLote(Collection<Long> idsRequerimento) {
        List<Long> distintos = List.copyOf(new LinkedHashSet<>(idsRequerimento));
        Map<Long, LeituraDosLancamentos> resultado = new LinkedHashMap<>();
        for (int inicio = 0; inicio < distintos.size(); inicio += MAXIMO_POR_LOTE) {
            List<Long> parte = distintos.subList(inicio, Math.min(distintos.size(), inicio + MAXIMO_POR_LOTE));
            Map<Long, LeituraDosLancamentos> lidos = ler(parte);
            parte.forEach(id -> resultado.put(id, lidos.getOrDefault(id, LeituraDosLancamentos.NENHUM)));
        }
        return resultado;
    }

    /** Uma consulta para até {@value LancamentosDoRequerimento#MAXIMO_POR_LOTE} requerimentos. */
    private Map<Long, LeituraDosLancamentos> ler(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        int limiteAtual = Math.max(1, limite.getAsInt());
        Map<Long, List<Lancamento>> porRequerimento = new HashMap<>();
        Set<Long> alemDoLimite = new HashSet<>();
        jdbc.query(sql, new MapSqlParameterSource()
                .addValue("idsRequerimento", ids)
                .addValue("limiteMaisUm", limiteAtual + 1)
                .addValue("maximoDeLinhas", ids.size() * (limiteAtual + 1)), rs -> {
                    long idRequerimento = rs.getLong("ID_REQUERIMENTO");
                    if (rs.getInt("POSICAO") > limiteAtual) {
                        alemDoLimite.add(idRequerimento);
                        return;
                    }
                    porRequerimento.computeIfAbsent(idRequerimento, k -> new ArrayList<>()).add(lancamento(rs));
                });
        Map<Long, LeituraDosLancamentos> resultado = new HashMap<>();
        porRequerimento.forEach((id, lista) ->
                resultado.put(id, new LeituraDosLancamentos(lista, alemDoLimite.contains(id))));
        return resultado;
    }

    private static Lancamento lancamento(ResultSet rs) throws SQLException {
        return new Lancamento(rs.getLong("ID"), rs.getLong("ID_REQUERIMENTO"), rs.getString("STATUS"),
                umOuSim(rs.getString("TAXA_ISENTA")), rs.getString("STATUS_PROCESSADO"), rs.getString("NUM_DAR"),
                DataHoraDoSiga.ler(rs, "DATA_EMISSAO"), DataHoraDoSiga.ler(rs, "DATA_VENCIMENTO"),
                DataHoraDoSiga.ler(rs, "DATA_PAGAMENTO"), DataHoraDoSiga.ler(rs, "DATA_FIM"),
                rs.getBigDecimal("VALOR_REAL"), rs.getBigDecimal("VALOR_DESCONTO"), rs.getBigDecimal("QTDE_TOTAL_UPF"),
                indicador(rs.getString("PASSOU_POR_REAPROV_TAXA_CORREC")));
    }

    /** {@code TAXA_ISENTA}: só {@code 1} é isenta (tipo real NÃO CONFIRMADO; docs/10). */
    private static boolean umOuSim(String valor) {
        return valor != null && valor.strip().equals("1");
    }

    /** Indicador de diagnóstico: {@code 1} sim, {@code 0} não, qualquer outro valor (ou nulo) desconhecido. */
    private static Boolean indicador(String valor) {
        if (valor == null) {
            return null;
        }
        return switch (valor.strip()) {
            case "1" -> Boolean.TRUE;
            case "0" -> Boolean.FALSE;
            default -> null;
        };
    }
}
