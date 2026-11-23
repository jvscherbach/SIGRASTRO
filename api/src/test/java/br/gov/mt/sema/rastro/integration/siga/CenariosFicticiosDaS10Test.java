package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS10.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
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
 * O construtor grava exatamente a população do painel da S10 (PN01 a PN16), com as contagens conferidas. Lê pelo usuário de
 * leitura. As consultas daqui conferem as fixtures: não são o SQL de produção (bloco 2).
 */
@Testcontainers(disabledWithoutDocker = true)
class CenariosFicticiosDaS10Test {

    private static final String FAIXA = "ID BETWEEN 10001 AND 12999";
    private static final String NO_ANO = "DATA_PROTOCOLO_PROCESSO >= TIMESTAMP '2026-01-01 00:00:00' "
            + "AND DATA_PROTOCOLO_PROCESSO < TIMESTAMP '2027-01-01 00:00:00'";

    private static HikariDataSource dataSource;
    private static NamedParameterJdbcTemplate jdbc;
    private static Map<String, Resultado> cenarios;

    @BeforeAll
    static void abrir() {
        cenarios = SigaOracleTeste.cenariosDoPainel();
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

    private static int[] porMes(String sql) {
        int[] meses = new int[12];
        jdbc.query(sql, (rs) -> {
            meses[rs.getInt("MES") - 1] = rs.getInt("TOTAL");
        });
        return meses;
    }

    /** Tarefas ativas da raiz do requerimento: chave da tarefa e (CPF fictício de teste) responsável. */
    private record Tarefa(String chave, String responsavel, String grupo) {
    }

    private static List<Tarefa> tarefasAtivas(long id) {
        return jdbc.query("""
                SELECT t.TASK_DEF_KEY_, t.ASSIGNEE_,
                       (SELECT MIN(l.GROUP_ID_) FROM SIGA2_CAMUNDA.ACT_RU_IDENTITYLINK l WHERE l.TASK_ID_ = t.ID_) AS GRUPO
                FROM   SIGA2_CAMUNDA.ACT_RU_TASK t
                JOIN   SIGA2_CAMUNDA.ACT_RU_EXECUTION e ON e.ID_ = t.PROC_INST_ID_ AND e.PARENT_ID_ IS NULL
                JOIN   SIGA2_CAMUNDA.ACT_HI_PROCINST p ON p.ID_ = e.PROC_INST_ID_
                WHERE  e.BUSINESS_KEY_ = :id AND p.PROC_DEF_KEY_ <> 'procGestaoComplementacao'
                ORDER BY t.TASK_DEF_KEY_""", new MapSqlParameterSource("id", Long.toString(id)),
                (rs, n) -> new Tarefa(rs.getString(1), rs.getString(2), rs.getString("GRUPO")));
    }

    @Test
    void todosOsCenariosForamGravados() {
        assertThat(cenarios).hasSize(idsSemVolume().size() + PN16_TOTAL);
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE " + FAIXA))
                .isEqualTo(idsSemVolume().size() + PN16_TOTAL);
        for (long id : idsSemVolume()) {
            assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = " + id)).as("id %d", id)
                    .isEqualTo(1);
        }
    }

    @Test
    void pn01aPn04ProtocoladosPorMesNoAnoPorIntervalo() {
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE " + FAIXA + " AND " + NO_ANO))
                .isEqualTo(BASE_DO_ANO);
        int[] sem = porMes("""
                SELECT EXTRACT(MONTH FROM DATA_PROTOCOLO_PROCESSO) AS MES, COUNT(*) AS TOTAL
                FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO
                WHERE  ID BETWEEN 10001 AND 10999 AND %s
                GROUP BY EXTRACT(MONTH FROM DATA_PROTOCOLO_PROCESSO)""".formatted(NO_ANO));
        assertThat(sem).containsExactly(PROTOCOLADOS_POR_MES_SEM_VOLUME);
        // janeiro e dezembro, dois no mesmo mês (março)
        assertThat(sem[0]).isPositive();
        assertThat(sem[11]).isPositive();
        assertThat(sem[2]).isEqualTo(2);
    }

    @Test
    void pn02ConcluidosPorMesAproximadoSaoSubconjuntoDosProtocolados() {
        int[] concluidos = porMes("""
                SELECT EXTRACT(MONTH FROM DATA_ALTERACAO) AS MES, COUNT(*) AS TOTAL
                FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO
                WHERE  %s AND %s AND SITUACAO_PROCESSO = 'CONCLUIDO'
                GROUP BY EXTRACT(MONTH FROM DATA_ALTERACAO)""".formatted(FAIXA, NO_ANO));
        assertThat(concluidos).containsExactly(CONCLUIDOS_POR_MES);
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE " + FAIXA + " AND "
                + NO_ANO + " AND SITUACAO_PROCESSO = 'CONCLUIDO'")).isEqualTo(CONCLUIDOS);
        // concluídos em mês diferente do protocolado
        assertThat(inteiro("""
                SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO
                WHERE ID BETWEEN 10011 AND 10013
                AND EXTRACT(MONTH FROM DATA_ALTERACAO) = EXTRACT(MONTH FROM DATA_PROTOCOLO_PROCESSO)""")).isZero();
    }

    @Test
    void pn03OFora2025EOSemDataNaoEntramNaBase() {
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = " + PN03_DE_2025 + " AND "
                + NO_ANO)).isZero();
        // concluído em 2026, mas protocolado em 2025: nunca entra nos concluídos do ano
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = " + PN03_DE_2025
                + " AND SITUACAO_PROCESSO = 'CONCLUIDO' AND EXTRACT(YEAR FROM DATA_ALTERACAO) = 2026")).isEqualTo(1);
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = " + PN03_DE_2025
                + " AND NUMERO = '" + PN03_DE_2025 + "/2025'")).isEqualTo(1);
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = " + PN03_SEM_DATA
                + " AND DATA_PROTOCOLO_PROCESSO IS NULL")).isEqualTo(1);
    }

    @Test
    void pn04CanceladoEArquivadoSaoProtocoladosMasForaDaFila() {
        for (long id : new long[] {PN04_CANCELADO, PN04_ARQUIVADO}) {
            assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = " + id + " AND " + NO_ANO))
                    .isEqualTo(1);
            assertThat(tarefasAtivas(id)).as("tarefas ativas de %d", id).isEmpty();
            assertThat(cenarios.get("PN04/" + id).encerrada()).isTrue();
        }
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = " + PN04_CANCELADO
                + " AND SITUACAO_PROCESSO = 'CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE'")).isEqualTo(1);
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = " + PN04_ARQUIVADO
                + " AND SITUACAO_PROCESSO = 'ARQUIVADO_DEFINITIVAMENTE'")).isEqualTo(1);
        // PN02 e PN04 somam os 5 fora da fila (PN03 de 2025 está fora da base)
        assertThat(CONCLUIDOS + 2).isEqualTo(FORA_DA_FILA_SEM_VOLUME);
    }

    @Test
    void pn05OsTresServidoresTemAsTarefasDescritas() {
        assertThat(tarefasAtivas(PN05_ANALISE_SERVIDOR_01)).singleElement()
                .satisfies(t -> assertThat(t).isEqualTo(new Tarefa("taskAnalisarRequerimento", "00000000001", null)));
        assertThat(tarefasAtivas(PN05_REVISAO_SERVIDOR_02)).singleElement()
                .satisfies(t -> assertThat(t).isEqualTo(new Tarefa("taskRevisarParecerTecnico", "00000000002", null)));
        assertThat(tarefasAtivas(PN05_VALIDACAO_SERVIDOR_03)).singleElement()
                .satisfies(t -> assertThat(t).isEqualTo(new Tarefa("taskValidarParecerTecnico", "00000000003", null)));
        assertThat(tarefasAtivas(PN05_ANALISE_SERVIDOR_01_B)).singleElement()
                .satisfies(t -> assertThat(t.responsavel()).isEqualTo("00000000001"));
        assertThat(tarefasAtivas(PN05_ACEITE_SERVIDOR_02)).singleElement()
                .satisfies(t -> assertThat(t).isEqualTo(new Tarefa("taskAceitarAtribuicao", "00000000002", null)));
    }

    @Test
    void pn06LacEmEtapasDoLac() {
        assertThat(tarefasAtivas(PN06_ENVIO_DO_TERMO)).singleElement()
                .satisfies(t -> assertThat(t.chave()).isEqualTo("taskEnviarTermoAdesao"));
        assertThat(tarefasAtivas(PN06_CONFERENCIA_DE_GRUPO)).singleElement()
                .satisfies(t -> assertThat(t.grupo()).isEqualTo("GrpProcResponsavelAtendimentoLac"));
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID IN (" + PN06_ENVIO_DO_TERMO + ", "
                + PN06_CONFERENCIA_DE_GRUPO + ") AND ID_TIPO_PROCESSO = 72")).isEqualTo(2);
    }

    @Test
    void pn07DuasTarefasAtivasEmRamosDiferentes() {
        assertThat(tarefasAtivas(PN07_PARALELA)).extracting(Tarefa::chave)
                .containsExactly("taskAnalisarRequerimento", "taskCorrecaoRequerimento");
        assertThat(cenarios.get("PN07/" + PN07_PARALELA).tarefasAtivasNaRaiz()).isEqualTo(2);
    }

    @Test
    void pn08ComplementacaoEmAndamentoNaoViraTarefaDaRaiz() {
        assertThat(tarefasAtivas(PN08_COMPLEMENTACAO)).singleElement()
                .satisfies(t -> assertThat(t.chave()).isEqualTo("taskAnalisarRequerimento"));
        // a instância da complementação existe, com a mesma chave de negócio, e tem a sua tarefa
        assertThat(inteiro("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_EXECUTION
                WHERE BUSINESS_KEY_ = '%d' AND PARENT_ID_ IS NULL""".formatted(PN08_COMPLEMENTACAO))).isEqualTo(2);
        assertThat(cenarios.get("PN08/" + PN08_COMPLEMENTACAO).complementacao()).isTrue();
    }

    @Test
    void pn09aPn13GrupoInteressadoSemAtribuicaoSemServidorENaoMapeada() {
        assertThat(tarefasAtivas(PN09_GRUPO)).singleElement()
                .satisfies(t -> assertThat(t).isEqualTo(new Tarefa("taskAtribuirRequerimento", null,
                        "grpSurhAtribuirRequerimentoSuperficial")));
        assertThat(tarefasAtivas(PN10_INTERESSADO)).singleElement().satisfies(t -> {
            assertThat(t.chave()).isEqualTo("taskCadastrarRequerimento");
            assertThat(t.responsavel()).isEqualTo(CamundaFicticioBuilder.CPF_DO_INTERESSADO);
        });
        assertThat(tarefasAtivas(PN11_SEM_ATRIBUICAO)).singleElement()
                .satisfies(t -> assertThat(t).isEqualTo(new Tarefa("taskRevisarParecerTecnico", null, null)));
        assertThat(tarefasAtivas(PN12_SEM_SERVIDOR)).singleElement()
                .satisfies(t -> assertThat(t.responsavel()).isEqualTo(CamundaFicticioBuilder.CPF_SEM_SERVIDOR));
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.SERVIDOR WHERE CPF = '"
                + CamundaFicticioBuilder.CPF_SEM_SERVIDOR + "'")).isZero();
        assertThat(tarefasAtivas(PN13_NAO_MAPEADA)).singleElement()
                .satisfies(t -> assertThat(t.chave()).isEqualTo(TAREFA_NAO_MAPEADA));
    }

    @Test
    void pn14ForaDoCatalogoEPn15SemInstancia() {
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = " + PN14_FORA_DO_CATALOGO
                + " AND ID_TIPO_PROCESSO = 73 AND " + NO_ANO)).isEqualTo(1);
        for (long id : new long[] {PN14_FORA_DO_CATALOGO, PN15_SEM_INSTANCIA}) {
            assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST WHERE BUSINESS_KEY_ = '" + id + "'"))
                    .as("instância de %d", id).isZero();
            assertThat(cenarios.get((id == PN14_FORA_DO_CATALOGO ? "PN14/" : "PN15/") + id).temInstancia()).isFalse();
        }
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = " + PN15_SEM_INSTANCIA
                + " AND ID_TIPO_PROCESSO = 71")).isEqualTo(1);
    }

    @Test
    void pn16VolumeDe1200DistribuidoPorMesEPorEtapa() {
        String volume = "ID BETWEEN " + PN16_PRIMEIRO + " AND " + (PN16_PRIMEIRO + PN16_TOTAL - 1);
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE " + volume + " AND " + NO_ANO))
                .isEqualTo(PN16_TOTAL);
        int[] meses = porMes("""
                SELECT EXTRACT(MONTH FROM DATA_PROTOCOLO_PROCESSO) AS MES, COUNT(*) AS TOTAL
                FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE %s
                GROUP BY EXTRACT(MONTH FROM DATA_PROTOCOLO_PROCESSO)""".formatted(volume));
        assertThat(meses).containsOnly(PN16_POR_MES);
        Map<String, Integer> etapas = new HashMap<>();
        jdbc.query("""
                SELECT t.TASK_DEF_KEY_, COUNT(*) AS TOTAL
                FROM   SIGA2_CAMUNDA.ACT_RU_TASK t
                JOIN   SIGA2_CAMUNDA.ACT_RU_EXECUTION e ON e.ID_ = t.PROC_INST_ID_ AND e.PARENT_ID_ IS NULL
                WHERE  LENGTH(e.BUSINESS_KEY_) = 5 AND e.BUSINESS_KEY_ BETWEEN '%d' AND '%d'
                GROUP BY t.TASK_DEF_KEY_""".formatted(PN16_PRIMEIRO, PN16_PRIMEIRO + PN16_TOTAL - 1),
                (rs) -> {
                    etapas.put(rs.getString(1), rs.getInt(2));
                });
        assertThat(etapas).containsOnlyKeys("taskConferirDocumentosApresentados", "taskAtribuirRequerimento",
                "taskRevisarParecerTecnico", "taskCadastrarRequerimento");
        assertThat(etapas.values()).containsOnly(PN16_POR_ETAPA);
        assertThat(cenarios.keySet().stream().filter(c -> c.startsWith("PN16/")).count()).isEqualTo(PN16_TOTAL);
    }

    @Test
    void nenhumDadoRealSoCpfsFicticiosInvalidos() {
        // Todo responsável gravado na população é do padrão fictício 000000000NN (NN diferente de 00).
        int forasDoPadrao = inteiro("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_TASK t
                JOIN   SIGA2_CAMUNDA.ACT_RU_EXECUTION e ON e.ID_ = t.PROC_INST_ID_ AND e.PARENT_ID_ IS NULL
                WHERE  LENGTH(e.PROC_INST_ID_) = 10 AND e.PROC_INST_ID_ LIKE 'fict-1%' AND t.ASSIGNEE_ IS NOT NULL
                AND    NOT REGEXP_LIKE(t.ASSIGNEE_, '^000000000[0-9]{2}$')""");
        assertThat(forasDoPadrao).isZero();
        assertThat(inteiro("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE " + FAIXA
                + " AND ID_PESSOA <> " + CamundaFicticioBuilder.ID_INTERESSADO)).isZero();
    }
}
