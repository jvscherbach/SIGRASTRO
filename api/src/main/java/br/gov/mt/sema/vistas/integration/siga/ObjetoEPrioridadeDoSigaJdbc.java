package br.gov.mt.sema.vistas.integration.siga;

import java.util.ArrayList;
import java.util.List;

import br.gov.mt.sema.vistas.consulta.application.ObjetoEPrioridadeDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento.AtividadeLicenciamento;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento.FinalidadeAtividade;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento.TipoObjetivo;
import br.gov.mt.sema.vistas.consulta.domain.PrioridadeAtendimento;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Objeto e prioridade do requerimento (docs/04, P01). Cada bloco é uma consulta própria, porque objetivos e pares
 * finalidade/atividade são listas independentes (juntá-las multiplicaria linhas). Falhas seguem a tabela da S1:
 * 503 quando o SIGA está indisponível, 500 nos demais casos.
 */
@Repository
@ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
class ObjetoEPrioridadeDoSigaJdbc implements ObjetoEPrioridadeDoRequerimento {

    private final NamedParameterJdbcTemplate jdbc;
    private final String sqlObjetivosOutorga = SigaSql.carregar("objetivos-outorga");
    private final String sqlFinalidadesAtividades = SigaSql.carregar("finalidades-atividades-outorga");
    private final String sqlObjetoLicenciamento = SigaSql.carregar("objeto-licenciamento");
    private final String sqlPrioridade = SigaSql.carregar("prioridade");

    ObjetoEPrioridadeDoSigaJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate) {
        this.jdbc = sigaJdbcTemplate;
    }

    @Override
    public ObjetoDoRequerimento objeto(long idRequerimento) {
        return SigaFalhas.traduzir(() -> {
            MapSqlParameterSource id = new MapSqlParameterSource("idRequerimento", idRequerimento);

            List<String> objetivos = new ArrayList<>();
            List<TipoObjetivo> tipos = new ArrayList<>();
            jdbc.query(sqlObjetivosOutorga, id, rs -> {
                String objetivo = rs.getString("DESCRICAO");
                objetivos.add(objetivo);
                tipos.add(TipoObjetivo.de(rs.getString("TIPO_OUTORGA"), objetivo));
            });

            List<FinalidadeAtividade> pares = jdbc.query(sqlFinalidadesAtividades, id,
                    (rs, n) -> new FinalidadeAtividade(rs.getString("FINALIDADE"), rs.getString("ATIVIDADE")));

            AtividadeLicenciamento[] atividade = new AtividadeLicenciamento[1];
            jdbc.query(sqlObjetoLicenciamento, id, rs -> {
                if (atividade[0] == null) {
                    atividade[0] = new AtividadeLicenciamento(rs.getString("GRUPO"), rs.getString("ATIVIDADE"));
                }
                String objetivo = rs.getString("OBJETIVO");
                if (objetivo != null) {
                    objetivos.add(objetivo);
                }
            });

            return new ObjetoDoRequerimento(objetivos, pares, tipos, atividade[0]);
        });
    }

    @Override
    public PrioridadeAtendimento prioridade(long idRequerimento) {
        return SigaFalhas.traduzir(() -> {
            List<Integer> codigos = jdbc.query(sqlPrioridade, new MapSqlParameterSource("idRequerimento", idRequerimento),
                    (rs, n) -> rs.getInt("CODIGO"));
            return PrioridadeAtendimento.de(codigos);
        });
    }
}
