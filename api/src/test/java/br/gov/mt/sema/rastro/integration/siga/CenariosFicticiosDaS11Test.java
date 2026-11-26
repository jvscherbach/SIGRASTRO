package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS11.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Resultado;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * O construtor grava a população da caixa da S11 (CX01 a CX17 e o volume) com o que a tabela do cenário diz. Lê pelo usuário
 * de leitura. As consultas daqui conferem as fixtures: não são o SQL de produção (bloco 4).
 */
@Testcontainers(disabledWithoutDocker = true)
class CenariosFicticiosDaS11Test {

    private static final String FAIXA = "ID BETWEEN 15001 AND 15099";

    private static HikariDataSource dataSource;
    private static NamedParameterJdbcTemplate jdbc;
    private static Map<String, Resultado> cenarios;

    @BeforeAll
    static void abrir() {
        cenarios = SigaOracleTeste.cenariosDaCaixa();
        dataSource = SigaOracleTeste.dataSourceLeitura();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
    }

    private static int inteiro(String sql) {
        return jdbc.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
    }

    /** Tarefas ativas da raiz: chave da tarefa e responsável (CPF fictício). */
    private record Tarefa(String chave, String responsavel) {
    }

    private static List<Tarefa> tarefasAtivas(long id) {
        return jdbc.query("""
                SELECT t.TASK_DEF_KEY_, t.ASSIGNEE_
                FROM   SIGA2_CAMUNDA.ACT_RU_TASK t
                JOIN   SIGA2_CAMUNDA.ACT_RU_EXECUTION e ON e.ID_ = t.PROC_INST_ID_ AND e.PARENT_ID_ IS NULL
                WHERE  e.BUSINESS_KEY_ = :id
                ORDER BY t.TASK_DEF_KEY_""", new MapSqlParameterSource("id", Long.toString(id)),
                (rs, n) -> new Tarefa(rs.getString(1), rs.getString(2)));
    }

    private static int tarefasConcluidas(long id, String chave, String responsavel) {
        return inteiro("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_TASKINST
                WHERE PROC_INST_ID_ = 'fict-%d' AND TASK_DEF_KEY_ = '%s' AND ASSIGNEE_ = '%s' AND DELETE_REASON_ = 'completed'"""
                .formatted(id, chave, responsavel));
    }

    @Test
    void todosOsCenariosForamGravadosComInteressadoEmAnoLongeDe2026() {
        assertThat(cenarios).hasSize(CASOS.size());
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE " + FAIXA)).isEqualTo(CASOS.size());
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE " + FAIXA
                + " AND (EXTRACT(YEAR FROM DATA_ABERTURA) < 2027 OR EXTRACT(YEAR FROM DATA_PROTOCOLO_PROCESSO) < 2027)"))
                .isZero();
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE " + FAIXA
                + " AND DATA_PROTOCOLO_PROCESSO IS NULL")).isZero();
        for (Caso caso : CASOS) {
            String nome = jdbc.queryForObject("""
                    SELECT p.NOME FROM SIGA2_LICENCIAMENTO.REQUERIMENTO r
                    JOIN SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = r.ID_PESSOA WHERE r.ID = :id""",
                    new MapSqlParameterSource("id", caso.id()), String.class);
            assertThat(nome).as(caso.codigo()).isEqualTo(caso.interessado());
        }
    }

    @Test
    void aTarefaAtivaDeCadaCasoEDoAnalistaEsperado() {
        assertThat(tarefasAtivas(CX01)).containsExactly(new Tarefa("taskAnalisarRequerimento", A1));
        assertThat(tarefasAtivas(CX02)).containsExactly(new Tarefa("taskRevisarParecerTecnico", A2));
        assertThat(tarefasAtivas(CX03)).containsExactly(new Tarefa("taskRevisarParecerTecnico", A1));
        assertThat(tarefasAtivas(CX04)).containsExactly(
                new Tarefa("taskCorrecaoRequerimento", CamundaFicticioBuilder.CPF_DO_INTERESSADO));
        assertThat(tarefasAtivas(CX05)).containsExactly(new Tarefa("taskAnalisarRequerimento", A1));
        assertThat(tarefasAtivas(CX06)).containsExactly(new Tarefa("taskRevisarParecerTecnico", A1));
        assertThat(tarefasAtivas(CX07)).containsExactly(new Tarefa("taskAnalisarRequerimento", A1));
        assertThat(tarefasAtivas(CX08)).containsExactly(new Tarefa("taskAnalisarRequerimento", A1));
        assertThat(tarefasAtivas(CX09)).containsExactly(new Tarefa("taskAnalisarRequerimento", A1));
        assertThat(tarefasAtivas(CX10)).containsExactly(new Tarefa("taskValidarParecerTecnico", A1));
        // CX11: a emissão do ato está nas instâncias filhas; a raiz não tem tarefa ativa
        assertThat(tarefasAtivas(CX11)).isEmpty();
        assertThat(cenarios.get("CX11/" + CX11).filhas()).isPositive();
        assertThat(tarefasAtivas(CX12)).isEmpty();
        assertThat(cenarios.get("CX12/" + CX12).encerrada()).isTrue();
        assertThat(tarefasAtivas(CX13)).containsExactly(
                new Tarefa("taskCorrecaoRequerimento", CamundaFicticioBuilder.CPF_DO_INTERESSADO));
        assertThat(tarefasAtivas(CX14)).containsExactly(new Tarefa("taskAnalisarRequerimento", A1));
        assertThat(tarefasAtivas(CX15)).containsExactly(
                new Tarefa("taskCorrecaoRequerimento", CamundaFicticioBuilder.CPF_DO_INTERESSADO));
        assertThat(tarefasAtivas(CX16)).containsExactly(new Tarefa("taskAnalisarRequerimento", A3));
        assertThat(tarefasAtivas(CX17)).containsExactly(new Tarefa("taskAnalisarRequerimento", A1));
    }

    @Test
    void oHistoricoEDeCadaRetornoEstaGravado() {
        // CX04 e CX13: a última tarefa de análise concluída é do analista dono da correção; CX15 não teve análise
        assertThat(tarefasConcluidas(CX04, "taskAnalisarRequerimento", A1)).isEqualTo(1);
        assertThat(tarefasConcluidas(CX13, "taskAnalisarRequerimento", A2)).isEqualTo(1);
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_TASKINST WHERE PROC_INST_ID_ = 'fict-" + CX15
                + "' AND TASK_DEF_KEY_ = 'taskAnalisarRequerimento'")).isZero();
        // CX05: a correção foi concluída antes de voltar para a análise
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_TASKINST WHERE PROC_INST_ID_ = 'fict-" + CX05
                + "' AND TASK_DEF_KEY_ = 'taskCorrecaoRequerimento' AND DELETE_REASON_ = 'completed'")).isEqualTo(1);
        // CX06 e CX17: a emissão do ato teve uma filha devolvida
        for (long id : new long[] {CX06, CX17}) {
            assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST WHERE ROOT_PROC_INST_ID_ = 'fict-" + id
                    + "' AND STATE_ = 'INTERNALLY_TERMINATED'")).as("filha devolvida de %d", id).isEqualTo(1);
        }
        // CX07: a validação decidiu devolver; CX08: a revisão devolveu para a análise
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_VARINST WHERE PROC_INST_ID_ = 'fict-" + CX07
                + "' AND NAME_ = 'decisaoValidacaoParecerTecnico' AND TEXT_ = 'RA'")).isEqualTo(1);
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_VARINST WHERE PROC_INST_ID_ = 'fict-" + CX08
                + "' AND NAME_ = 'isRevisaoAnalise'")).isEqualTo(1);
        // CX09: processo novo, sem nenhuma análise antes
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_TASKINST WHERE PROC_INST_ID_ = 'fict-" + CX09
                + "' AND TASK_DEF_KEY_ IN ('taskAnalisarRequerimento', 'taskRevisarParecerTecnico') AND END_TIME_ IS NOT NULL"))
                .isZero();
    }

    @Test
    void osTresAnalistasTemRequerimentosEOMesmoInteressadoApareceEmDois() {
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_TASK t JOIN SIGA2_CAMUNDA.ACT_RU_EXECUTION e "
                + "ON e.ID_ = t.PROC_INST_ID_ AND e.PARENT_ID_ IS NULL WHERE e.BUSINESS_KEY_ BETWEEN '15001' AND '15099' "
                + "AND t.ASSIGNEE_ = '" + A3 + "'")).isEqualTo(1);
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE " + FAIXA + " AND ID_PESSOA = " + P1))
                .isEqualTo(4);
        // CX01 e CX16 são de analistas diferentes e do mesmo interessado
        assertThat(inteiro("SELECT COUNT(DISTINCT ID_PESSOA) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID IN (" + CX01 + ", "
                + CX16 + ")")).isEqualTo(1);
    }

    @Test
    void osCpfsGravadosSaoFicticiosEInvalidos() {
        assertThat(inteiro("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_TASK t
                JOIN   SIGA2_CAMUNDA.ACT_RU_EXECUTION e ON e.ID_ = t.PROC_INST_ID_ AND e.PARENT_ID_ IS NULL
                WHERE  e.BUSINESS_KEY_ BETWEEN '15001' AND '15099' AND t.ASSIGNEE_ IS NOT NULL
                AND    NOT REGEXP_LIKE(t.ASSIGNEE_, '^000000000[0-9]{2}$')""")).isZero();
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.PESSOA WHERE ID BETWEEN 15001 AND 15099 "
                + "AND NOT REGEXP_LIKE(CPF_CNPJ, '^0000[0-9]{7,10}$')")).isZero();
    }

    @Test
    void oVolumeTem1200RequerimentosNaAnaliseRepartidosEntreOsTresAnalistas() {
        Map<String, Resultado> volume = SigaOracleTeste.volumeDaCaixa();
        assertThat(volume).hasSize(VOLUME_TOTAL);
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID BETWEEN " + VOLUME_PRIMEIRO + " AND "
                + (VOLUME_PRIMEIRO + VOLUME_TOTAL - 1))).isEqualTo(VOLUME_TOTAL);
        for (String analista : List.of(A1, A2, A3)) {
            assertThat(inteiro("""
                    SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_TASK t
                    JOIN   SIGA2_CAMUNDA.ACT_RU_EXECUTION e ON e.ID_ = t.PROC_INST_ID_ AND e.PARENT_ID_ IS NULL
                    WHERE  e.BUSINESS_KEY_ BETWEEN '%d' AND '%d' AND t.TASK_DEF_KEY_ = 'taskAnalisarRequerimento'
                    AND    t.ASSIGNEE_ = '%s'""".formatted(VOLUME_PRIMEIRO, VOLUME_PRIMEIRO + VOLUME_TOTAL - 1, analista)))
                    .as(analista).isEqualTo(VOLUME_POR_ANALISTA);
        }
    }
}
