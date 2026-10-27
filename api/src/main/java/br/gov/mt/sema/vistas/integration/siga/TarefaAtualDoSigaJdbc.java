package br.gov.mt.sema.vistas.integration.siga;

import java.util.List;

import br.gov.mt.sema.vistas.consulta.application.TarefaAtualDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.ResponsavelTarefa;
import br.gov.mt.sema.vistas.consulta.domain.TarefaAtual;
import br.gov.mt.sema.vistas.integration.siga.dto.TarefaLinha;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tarefa atual do fluxo, lida das tabelas do motor de fluxo do SIGA. Falha na leitura não derruba o detalhe:
 * devolve INDISPONIVEL e registra no log.
 */
@Repository
@ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
class TarefaAtualDoSigaJdbc implements TarefaAtualDoRequerimento {

    private static final Logger log = LoggerFactory.getLogger(TarefaAtualDoSigaJdbc.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final String sql = SigaSql.carregar("tarefa-atual");

    TarefaAtualDoSigaJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate) {
        this.jdbc = sigaJdbcTemplate;
    }

    @Override
    public TarefaAtual obter(long idRequerimento) {
        try {
            List<TarefaLinha> linhas = jdbc.query(sql, new MapSqlParameterSource("idRequerimento", idRequerimento),
                    (rs, n) -> new TarefaLinha(rs.getString("NOME_TAREFA"),
                            DataHoraDoSiga.ler(rs, "CRIADA_EM"), rs.getString("FLUXO"),
                            rs.getString("SITUACAO_RESPONSAVEL"), rs.getString("NOME_RESPONSAVEL")));
            if (linhas.isEmpty()) {
                return TarefaAtual.semTarefa();
            }
            TarefaLinha t = linhas.get(0);
            return TarefaAtual.ativa(t.nome(), t.criadaEm(), responsavel(t), t.fluxo());
        } catch (RuntimeException e) {
            log.warn("Não foi possível ler a tarefa atual do requerimento {}: {}", idRequerimento, e.getMessage());
            return TarefaAtual.indisponivel();
        }
    }

    /** Situação desconhecida ou fora do esperado vira "não identificado": nunca expõe o dado cru. */
    private static ResponsavelTarefa responsavel(TarefaLinha t) {
        String situacao = t.situacaoResponsavel();
        if ("NAO_ATRIBUIDA".equals(situacao)) {
            return ResponsavelTarefa.naoAtribuida();
        }
        if ("ATRIBUIDO".equals(situacao)) {
            return ResponsavelTarefa.atribuido(t.nomeResponsavel());
        }
        return ResponsavelTarefa.naoIdentificado();
    }
}
