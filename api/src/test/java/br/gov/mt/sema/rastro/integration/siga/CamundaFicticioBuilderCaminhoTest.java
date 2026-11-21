package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.CHAVE_COMPLEMENTACAO;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.CORRECAO_EXPIRADA_FICTICIA;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.REPETICAO_POR_ERRO_FICTICIA;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.REPORT_KAFKA_FICTICIO;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.VARIAVEIS_PERMITIDAS;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Resultado;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.ResolucaoDeEtapa;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * O construtor grava exatamente o caminho que cada cenário da S8 descreve (HC01 a HC15, finais do Hídrico, LC01 a LC03,
 * finais do LAC e PB01 a PB03): a sequência de atividades encerradas, o que ficou aberto, as tarefas {@code deleted}, as
 * filhas, a complementação, as variáveis da lista branca e as linhas do plano B. Lê pelo usuário de leitura (prova também o
 * grant das tabelas novas). As consultas daqui conferem as fixtures: não são o SQL de produção (bloco 2).
 */
@Testcontainers(disabledWithoutDocker = true)
class CamundaFicticioBuilderCaminhoTest {

    private static final CatalogoDeEtapas CATALOGO = CatalogoDeEtapasDeTeste.padrao();
    private static final String CHAMADA_AUTORIZACOES = "taskEmitirAutorizacoes";
    private static final String CHAMADA_PORTARIAS = "taskEmitirPortarias";
    private static final String CHAMADA_OUTROS_ATOS = "taskEmitirOutrosAtos";
    private static final String CORPO = "#multiInstanceBody";

    private static HikariDataSource dataSource;
    private static NamedParameterJdbcTemplate jdbc;
    private static Map<String, Resultado> cenarios;

    @BeforeAll
    static void abrir() {
        cenarios = SigaOracleTeste.CENARIOS_S8;
        dataSource = SigaOracleTeste.dataSourceLeitura();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
    }

    // --- montagem das sequências esperadas

    private static List<String> seq(Object... partes) {
        List<String> lista = new ArrayList<>();
        for (Object parte : partes) {
            if (parte instanceof List<?> l) {
                l.forEach(x -> lista.add((String) x));
            } else {
                lista.add((String) parte);
            }
        }
        return lista;
    }

    /** Um ciclo multi-instância com {@code n} tarefas: o corpo e uma linha por tarefa. */
    private static List<String> ciclo(String id, int n) {
        List<String> l = new ArrayList<>();
        l.add(id + CORPO);
        l.addAll(Collections.nCopies(n, id));
        return l;
    }

    private static List<String> vezes(String id, int n) {
        return new ArrayList<>(Collections.nCopies(n, id));
    }

    /** Cadastro até o aceite pelos três analistas, taxável. */
    private static List<String> taxavelAteAAnalise() {
        return seq(CADASTRO, CONFERENCIA, EMITIR_TAXA, PAGAMENTO, PROTOCOLO, ATRIBUICAO, ciclo(ACEITE, 3));
    }

    private static List<String> validacaoAteAEmissao() {
        return seq(taxavelAteAAnalise(), ciclo(ANALISE, 3), REVISAO, ciclo(VALIDACAO, 3));
    }

    private static List<String> correcaoAteACorrecao() {
        return seq(taxavelAteAAnalise(), ciclo(ANALISE, 3), CORRECAO_STATUS, CORRECAO);
    }

    // --- consultas de conferência

    private static String pi(long id) {
        return "fict-" + id;
    }

    private static List<String> encerradas(long id) {
        return jdbc.queryForList("""
                SELECT TO_CHAR(ACT_ID_) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST
                WHERE  PROC_INST_ID_ = :pi AND END_TIME_ IS NOT NULL
                ORDER BY START_TIME_, ID_""", new MapSqlParameterSource("pi", pi(id)), String.class);
    }

    private static List<String> abertas(long id) {
        return jdbc.queryForList("""
                SELECT TO_CHAR(ACT_ID_) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST
                WHERE  PROC_INST_ID_ = :pi AND END_TIME_ IS NULL AND TO_CHAR(ACT_TYPE_) <> 'multiInstanceBody'
                ORDER BY START_TIME_, ID_""", new MapSqlParameterSource("pi", pi(id)), String.class);
    }

    private static int contar(String sql, Object... pares) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        for (int i = 0; i < pares.length; i += 2) {
            p.addValue((String) pares[i], pares[i + 1]);
        }
        Integer n = jdbc.queryForObject(sql, p, Integer.class);
        return n == null ? 0 : n;
    }

    private static int tarefasComMotivo(long id, String tarefa, String motivo) {
        return contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_TASKINST
                WHERE  PROC_INST_ID_ = :pi AND TASK_DEF_KEY_ = :tarefa AND DELETE_REASON_ = :motivo""",
                "pi", pi(id), "tarefa", tarefa, "motivo", motivo);
    }

    private static Map<String, Object> instancia(long id) {
        return jdbc.queryForMap("""
                SELECT TO_CHAR(ID_) AS ID, TO_CHAR(END_ACT_ID_) AS FIM, TO_CHAR(STATE_) AS ESTADO, START_TIME_ AS INICIO,
                       END_TIME_ AS TERMINO
                FROM   SIGA2_CAMUNDA.ACT_HI_PROCINST
                WHERE  TO_CHAR(BUSINESS_KEY_) = TO_CHAR(:id) AND SUPER_PROCESS_INSTANCE_ID_ IS NULL
                  AND  TO_CHAR(PROC_DEF_KEY_) <> :compl""",
                new MapSqlParameterSource().addValue("id", id).addValue("compl", CHAVE_COMPLEMENTACAO));
    }

    private static List<Map<String, Object>> filhas(long id) {
        return jdbc.queryForList("""
                SELECT TO_CHAR(PROC_DEF_KEY_) AS CHAVE, TO_CHAR(STATE_) AS ESTADO, END_TIME_ AS TERMINO,
                       TO_CHAR(DELETE_REASON_) AS MOTIVO
                FROM   SIGA2_CAMUNDA.ACT_HI_PROCINST
                WHERE  TO_CHAR(SUPER_PROCESS_INSTANCE_ID_) = :pi ORDER BY START_TIME_, ID_""",
                new MapSqlParameterSource("pi", pi(id)));
    }

    private static List<String> variaveisEmOrdem(long id, String nome) {
        return jdbc.queryForList("""
                SELECT TO_CHAR(TEXT_) FROM SIGA2_CAMUNDA.ACT_HI_DETAIL
                WHERE  PROC_INST_ID_ = :pi AND NAME_ = :nome AND TYPE_ = 'VariableUpdate' ORDER BY TIME_, SEQUENCE_COUNTER_""",
                new MapSqlParameterSource().addValue("pi", pi(id)).addValue("nome", nome), String.class);
    }

    private static String valorFinal(long id, String nome) {
        return jdbc.queryForObject("""
                SELECT TO_CHAR(TEXT_) FROM SIGA2_CAMUNDA.ACT_HI_VARINST WHERE PROC_INST_ID_ = :pi AND NAME_ = :nome""",
                new MapSqlParameterSource().addValue("pi", pi(id)).addValue("nome", nome), String.class);
    }

    private static long id(String codigo) {
        return cenarios.get(codigo).idRequerimento();
    }

    // --- o conjunto

    @Test
    void todosOsCenariosDaS8ForamGravadosNaFaixaPropria() {
        assertThat(cenarios.keySet()).containsExactlyInAnyOrder(
                "HC01", "HC02", "HC03", "HC04", "HC05", "HC06", "HC07", "HC08", "HC09", "HC10", "HC11", "HC12", "HC13",
                "HC14", "HC15", "HF01", "HF02", "HF03", "HF04", "LC01", "LC02", "LC03", "LF01", "LF02", "LF03", "LF04",
                "LF05", "PB01", "PB02", "PB03");
        assertThat(contar("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID BETWEEN 8000 AND 8199"))
                .isEqualTo(cenarios.size());
        assertThat(cenarios.values()).allMatch(r -> r.idRequerimento() >= 8000 && r.idRequerimento() <= 8199);
    }

    // --- sequência exata de atividades encerradas, por cenário

    @Test
    void hidricoSequenciaEncerradaDeCadaCenario() {
        assertThat(encerradas(HC01)).as("HC01").isEqualTo(seq(validacaoAteAEmissao(), CHAMADA_AUTORIZACOES,
                CHAMADA_PORTARIAS, PUBLICACAO));
        assertThat(encerradas(HC02)).as("HC02").isEqualTo(seq(CADASTRO, CONFERENCIA, PROTOCOLO, ATRIBUICAO, ciclo(ACEITE, 3)));
        assertThat(encerradas(HC03)).as("HC03").isEqualTo(seq(CADASTRO, CONFERENCIA, CADASTRO, CONFERENCIA, EMITIR_TAXA));
        assertThat(encerradas(HC04)).as("HC04").isEqualTo(seq(validacaoAteAEmissao(), ciclo(ANALISE, 3), REVISAO,
                ciclo(VALIDACAO, 3), REVISAO));
        assertThat(encerradas(HC05)).as("HC05").isEqualTo(seq(taxavelAteAAnalise(), ciclo(ANALISE, 3), REVISAO,
                ciclo(ANALISE, 3), REVISAO, ciclo(VALIDACAO, 3), ciclo(ANALISE, 3)));
        assertThat(encerradas(HC06)).as("HC06").isEqualTo(seq(CADASTRO, CONFERENCIA, EMITIR_TAXA, PAGAMENTO, PROTOCOLO,
                ATRIBUICAO, ciclo(ACEITE, 3), ATRIBUICAO, ciclo(ACEITE, 3), ciclo(ANALISE, 3), ATRIBUICAO));
        assertThat(encerradas(HC07)).as("HC07").isEqualTo(seq(correcaoAteACorrecao(), EMITIR_TAXA_CORRECAO,
                PAGAMENTO_CORRECAO));
        assertThat(encerradas(HC08)).as("HC08").isEqualTo(correcaoAteACorrecao());
        assertThat(encerradas(HC09)).as("HC09").isEqualTo(seq(correcaoAteACorrecao(), CORRECAO_EXPIRADA_FICTICIA));
        assertThat(encerradas(HC10)).as("HC10").isEqualTo(seq(taxavelAteAAnalise(), ciclo(ANALISE, 3), CORRECAO_STATUS));
        assertThat(encerradas(HC11)).as("HC11").isEqualTo(seq(CADASTRO, "Activity_0miwhxt", CONFERENCIA,
                REPORT_KAFKA_FICTICIO, vezes(EMITIR_TAXA, 4), REPETICAO_POR_ERRO_FICTICIA));
        assertThat(encerradas(HC12)).as("HC12").isEqualTo(seq(validacaoAteAEmissao(), CHAMADA_OUTROS_ATOS,
                CHAMADA_AUTORIZACOES, CHAMADA_PORTARIAS, REVISAO, ciclo(VALIDACAO, 3), CHAMADA_OUTROS_ATOS,
                CHAMADA_AUTORIZACOES, CHAMADA_PORTARIAS));
        assertThat(encerradas(HC13)).as("HC13").isEqualTo(seq(validacaoAteAEmissao(), CHAMADA_AUTORIZACOES, PUBLICACAO,
                VERIFICACAO, CHAMADA_OUTROS_ATOS));
        assertThat(encerradas(HC14)).as("HC14").isEqualTo(seq(taxavelAteAAnalise(), ciclo(ANALISE, 3)));
    }

    @Test
    void hidricoFinaisTemOEventoDeFimComoUltimaAtividade() {
        assertThat(encerradas(HF01)).as("HF01").isEqualTo(seq(validacaoAteAEmissao(), CHAMADA_AUTORIZACOES,
                CHAMADA_PORTARIAS, PUBLICACAO, VERIFICACAO, "Event_1wiuc48"));
        assertThat(encerradas(HF02)).as("HF02").isEqualTo(seq(CADASTRO, CONFERENCIA, "Event_1kq2k0z"));
        assertThat(encerradas(HF03)).as("HF03").isEqualTo(seq(CADASTRO, "Event_0abm55w"));
        assertThat(encerradas(HF04)).as("HF04").isEqualTo(seq(CADASTRO, CONFERENCIA, EMITIR_TAXA, PAGAMENTO, "Event_0h1oige"));
    }

    @Test
    void lacSequenciaEncerradaDeCadaCenario() {
        assertThat(encerradas(LC01)).as("LC01").isEqualTo(seq(CADASTRO, CONFERENCIA, EMITIR_TAXA, LAC_PAGAMENTO, PROTOCOLO,
                LAC_TERMO, LAC_ENVIO, LAC_CONCLUSAO, "evtProcessoConcluido"));
        assertThat(encerradas(LC02)).as("LC02").isEqualTo(seq(CADASTRO, CONFERENCIA, PROTOCOLO, LAC_TERMO));
        assertThat(encerradas(LC03)).as("LC03").isEqualTo(seq(CADASTRO, CONFERENCIA, CORRECAO, CONFERENCIA, EMITIR_TAXA));
        assertThat(encerradas(LF01)).as("LF01").isEqualTo(seq(CADASTRO, "evtFimRequerimentoAPedidoCadastrante"));
        assertThat(encerradas(LF02)).as("LF02").isEqualTo(seq(CADASTRO, "evtEncerrarRequerimentoPrazoCadExpirado"));
        assertThat(encerradas(LF03)).as("LF03").isEqualTo(seq(CADASTRO, CONFERENCIA, CORRECAO,
                "evtEncerrarProcessoPrazoCorrecaoExpirado"));
        assertThat(encerradas(LF04)).as("LF04").isEqualTo(seq(CADASTRO, CONFERENCIA, EMITIR_TAXA, LAC_PAGAMENTO,
                "evtEncerrarProcessoFaltaPagto"));
        assertThat(encerradas(LF05)).as("LF05").isEqualTo(seq(CADASTRO, CONFERENCIA, PROTOCOLO, LAC_TERMO, LAC_ENVIO,
                "Event_0x7hz1p"));
    }

    @Test
    void hc15TemMilharesDeAtividadesEPassaDoLimitePadraoDeLinhas() {
        assertThat(encerradas(HC15)).hasSize(2 + REPETICOES_DO_HC15);
        assertThat(encerradas(HC15)).startsWith(CADASTRO, CONFERENCIA).endsWith(EMITIR_TAXA);
        assertThat(contar("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST WHERE PROC_INST_ID_ = :pi", "pi", pi(HC15)))
                .isGreaterThan(3_000);
        // A ordem textual dos ids é a ordem de gravação: o desempate de START_TIME_ do bloco 2 é estável.
        List<String> pelaOrdemDoInicio = jdbc.queryForList("""
                SELECT TO_CHAR(ID_) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST WHERE PROC_INST_ID_ = :pi ORDER BY START_TIME_, ID_""",
                new MapSqlParameterSource("pi", pi(HC15)), String.class);
        List<String> pelaOrdemDoId = jdbc.queryForList("""
                SELECT TO_CHAR(ID_) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST WHERE PROC_INST_ID_ = :pi ORDER BY ID_""",
                new MapSqlParameterSource("pi", pi(HC15)), String.class);
        assertThat(pelaOrdemDoInicio).isEqualTo(pelaOrdemDoId);
    }

    // --- o que ficou aberto (etapa atual)

    @Test
    void oQueFicouAbertoEAEtapaAtualDeCadaCenarioAtivo() {
        Map<String, List<String>> esperado = Map.ofEntries(
                Map.entry("HC01", seq(VERIFICACAO)),
                Map.entry("HC02", vezes(ANALISE, 3)),
                Map.entry("HC03", seq(PAGAMENTO)),
                Map.entry("HC04", vezes(VALIDACAO, 3)),
                Map.entry("HC05", seq(REVISAO)),
                Map.entry("HC06", vezes(ACEITE, 3)),
                Map.entry("HC07", vezes(ANALISE, 3)),
                Map.entry("HC08", vezes(ANALISE, 3)),
                Map.entry("HC09", vezes(ANALISE, 3)),
                Map.entry("HC10", seq(CORRECAO)),
                Map.entry("HC11", seq(PAGAMENTO)),
                Map.entry("HC12", seq(PUBLICACAO)),
                Map.entry("HC13", seq(PUBLICACAO)),
                Map.entry("HC14", seq(REVISAO)),
                Map.entry("HC15", seq(PAGAMENTO)),
                Map.entry("LC02", seq(LAC_ENVIO)),
                Map.entry("LC03", seq(LAC_PAGAMENTO)));
        esperado.forEach((codigo, ids) -> assertThat(abertas(id(codigo))).as(codigo).isEqualTo(ids));
        // Instância encerrada não tem nada aberto.
        for (String codigo : List.of("HF01", "HF02", "HF03", "HF04", "LC01", "LF01", "LF02", "LF03", "LF04", "LF05")) {
            assertThat(abertas(id(codigo))).as(codigo).isEmpty();
        }
    }

    @Test
    void instanciaRaizUnicaComBusinessKeyEEstadoCoerenteComOFinal() {
        for (Resultado r : cenarios.values()) {
            if (!r.temInstancia()) {
                continue;
            }
            Map<String, Object> raiz = instancia(r.idRequerimento());
            assertThat(raiz.get("ID")).as("raiz de %d", r.idRequerimento()).isEqualTo(pi(r.idRequerimento()));
            if (r.encerrada()) {
                assertThat(raiz.get("ESTADO")).isEqualTo("COMPLETED");
                assertThat(raiz.get("FIM")).isNotNull();
                assertThat(raiz.get("TERMINO")).isNotNull();
            } else {
                assertThat(raiz.get("ESTADO")).isEqualTo("ACTIVE");
                assertThat(raiz.get("TERMINO")).isNull();
            }
            // O início da instância é o início da primeira atividade do caminho.
            Timestamp primeira = jdbc.queryForObject("""
                    SELECT MIN(START_TIME_) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST WHERE PROC_INST_ID_ = :pi""",
                    new MapSqlParameterSource("pi", pi(r.idRequerimento())), Timestamp.class);
            assertThat(((Timestamp) raiz.get("INICIO")).toLocalDateTime()).isEqualTo(primeira.toLocalDateTime());
        }
        assertThat(instancia(id("HF01")).get("FIM")).isEqualTo("Event_1wiuc48");
        assertThat(instancia(id("HF04")).get("FIM")).isEqualTo("Event_0h1oige");
        assertThat(instancia(id("LF03")).get("FIM")).isEqualTo("evtEncerrarProcessoPrazoCorrecaoExpirado");
        assertThat(instancia(id("LC01")).get("FIM")).isEqualTo("evtProcessoConcluido");
    }

    @Test
    void todaAtividadeDoCaminhoEstaNoCatalogoOuEFicticiaEvidente() {
        Set<String> ficticias = Set.of(REPORT_KAFKA_FICTICIO, REPETICAO_POR_ERRO_FICTICIA, CORRECAO_EXPIRADA_FICTICIA);
        for (Resultado r : cenarios.values()) {
            if (!r.temInstancia()) {
                continue;
            }
            String chave = jdbc.queryForObject("SELECT TO_CHAR(PROC_DEF_KEY_) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST WHERE ID_ = :pi",
                    new MapSqlParameterSource("pi", r.instancia()), String.class);
            ModeloDeFluxo modelo = CATALOGO.modelo(chave).orElseThrow();
            List<String> ids = jdbc.queryForList("""
                    SELECT DISTINCT TO_CHAR(ACT_ID_) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST WHERE PROC_INST_ID_ = :pi""",
                    new MapSqlParameterSource("pi", r.instancia()), String.class);
            for (String idDaAtividade : ids) {
                String semCorpo = idDaAtividade.replace(CORPO, "");
                ResolucaoDeEtapa resolucao = modelo.resolver(semCorpo, Set.of());
                if (resolucao instanceof ResolucaoDeEtapa.NaoMapeado) {
                    assertThat(ficticias).as("%s de %d", semCorpo, r.idRequerimento()).contains(semCorpo);
                }
                assertThat(resolucao).as("%s de %d", semCorpo, r.idRequerimento())
                        .isNotInstanceOf(ResolucaoDeEtapa.NaoSuportado.class);
            }
        }
    }

    // --- tarefas encerradas sem ação, filhas, ruído, variáveis, complementação

    @Test
    void tarefasEncerradasPelaCondicaoDeTerminoFicamDeleted() {
        // HC10: um analista pede correção (completed); as outras duas ficam 'deleted'.
        assertThat(tarefasComMotivo(HC10, ANALISE, "completed")).isEqualTo(1);
        assertThat(tarefasComMotivo(HC10, ANALISE, "deleted")).isEqualTo(2);
        // HC06: a recusa no aceite encerra as outras duas; os demais ciclos são todos completed.
        assertThat(tarefasComMotivo(HC06, ACEITE, "deleted")).isEqualTo(2);
        assertThat(tarefasComMotivo(HC06, ACEITE, "completed")).isEqualTo(1 + 3);
        // HC04: nenhuma tarefa deleted (devolução não encerra tarefas).
        assertThat(contar("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_TASKINST WHERE PROC_INST_ID_ = :pi AND DELETE_REASON_ = 'deleted'",
                "pi", pi(HC04))).isZero();
        // HC09: a correção expirada não foi concluída (hipótese de gravação: P40).
        assertThat(tarefasComMotivo(HC09, CORRECAO, "deleted")).isEqualTo(1);
        assertThat(tarefasComMotivo(HC09, CORRECAO, "completed")).isZero();
        // Só tarefa humana tem linha em ACT_HI_TASKINST: script, espera e evento de fim não.
        for (String script : List.of(EMITIR_TAXA, PROTOCOLO, CORRECAO_STATUS)) {
            assertThat(contar("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_TASKINST WHERE TASK_DEF_KEY_ = :t AND PROC_INST_ID_ = :pi",
                    "t", script, "pi", pi(HC07))).as(script).isZero();
        }
    }

    @Test
    void multiInstanciaTemCorpoETarefaPorParticipanteComResponsavelFicticio() {
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST
                WHERE  PROC_INST_ID_ = :pi AND TO_CHAR(ACT_ID_) = :corpo""", "pi", pi(HC01), "corpo", ANALISE + CORPO))
                .isEqualTo(1);
        List<String> assignees = jdbc.queryForList("""
                SELECT TO_CHAR(ASSIGNEE_) FROM SIGA2_CAMUNDA.ACT_HI_TASKINST
                WHERE  PROC_INST_ID_ = :pi AND TASK_DEF_KEY_ = :t ORDER BY ASSIGNEE_""",
                new MapSqlParameterSource().addValue("pi", pi(HC01)).addValue("t", ANALISE), String.class);
        assertThat(assignees).containsExactly("00000000005", "00000000006", "00000000007");
    }

    @Test
    void emissaoViraCallActivityEFilhasEncerradasLigadasPelaInstanciaMae() {
        List<Map<String, Object>> hc01 = filhas(HC01);
        assertThat(hc01).extracting(f -> f.get("CHAVE")).containsExactlyInAnyOrder("procSurhEmitirAutorizacoes",
                "procSurhEmissaoPortaria");
        assertThat(hc01).allSatisfy(f -> {
            assertThat(f.get("ESTADO")).isEqualTo("COMPLETED");
            assertThat(f.get("TERMINO")).isNotNull();
        });
        // As duas começam juntas e terminam em momentos diferentes: paralelas sobrepostas.
        List<Timestamp> inicios = jdbc.queryForList("""
                SELECT START_TIME_ FROM SIGA2_CAMUNDA.ACT_HI_PROCINST WHERE TO_CHAR(SUPER_PROCESS_INSTANCE_ID_) = :pi""",
                new MapSqlParameterSource("pi", pi(HC01)), Timestamp.class);
        assertThat(inicios).hasSize(2).containsOnly(inicios.getFirst());
        assertThat(hc01.get(0).get("TERMINO")).isNotEqualTo(hc01.get(1).get("TERMINO"));
        // O callActivity guarda a instância filha.
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST a
                JOIN   SIGA2_CAMUNDA.ACT_HI_PROCINST f ON TO_CHAR(f.ID_) = TO_CHAR(a.CALL_PROC_INST_ID_)
                WHERE  a.PROC_INST_ID_ = :pi AND TO_CHAR(a.ACT_TYPE_) = 'callActivity'
                  AND  TO_CHAR(f.SUPER_PROCESS_INSTANCE_ID_) = :pi""", "pi", pi(HC01))).isEqualTo(2);

        // HC12: duas emissões (3 + 3 filhas); uma filha da primeira foi devolvida.
        List<Map<String, Object>> hc12 = filhas(HC12);
        assertThat(hc12).hasSize(6);
        assertThat(hc12.stream().filter(f -> "INTERNALLY_TERMINATED".equals(f.get("ESTADO"))).count()).isEqualTo(1);
        assertThat(hc12.stream().filter(f -> "COMPLETED".equals(f.get("ESTADO"))).count()).isEqualTo(5);
        // HC13: a verificação REVISAR recria a emissão (uma filha antes da publicação, outra depois).
        assertThat(filhas(HC13)).hasSize(2);
    }

    @Test
    void ruidoDoHc11EstaPresenteParaOBloco3Colapsar() {
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST WHERE PROC_INST_ID_ = :pi AND TO_CHAR(ACT_ID_) = 'Activity_0miwhxt'""",
                "pi", pi(HC11))).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST WHERE PROC_INST_ID_ = :pi AND TO_CHAR(ACT_ID_) = :a",
                "pi", pi(HC11), "a", EMITIR_TAXA)).isEqualTo(4);
        assertThat(contar("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST WHERE PROC_INST_ID_ = :pi AND TO_CHAR(ACT_ID_) = :a",
                "pi", pi(HC11), "a", REPORT_KAFKA_FICTICIO)).isEqualTo(1);
        // A tarefa Suporte é tarefa humana concluída.
        assertThat(tarefasComMotivo(HC11, "Activity_0miwhxt", "completed")).isEqualTo(1);
    }

    @Test
    void variaveisSoDaListaBrancaEComValoresDaEpoca() {
        // statusConferencia é sobrescrito: o valor final não é o da primeira passagem (regra técnica 11).
        assertThat(variaveisEmOrdem(HC03, "statusConferencia")).containsExactly("reprovado", "taxavel");
        assertThat(valorFinal(HC03, "statusConferencia")).isEqualTo("taxavel");
        assertThat(variaveisEmOrdem(HC04, "decisaoValidacaoParecerTecnico")).containsExactly("RA", "RP");
        assertThat(variaveisEmOrdem(HC05, "isRevisaoAnalise")).containsExactly("true", "true");
        assertThat(variaveisEmOrdem(HC12, "isRevisaoParecer")).containsExactly("true");
        assertThat(variaveisEmOrdem(HC13, "decisaoVerificarRevisao")).containsExactly("REVISAR");
        assertThat(variaveisEmOrdem(HC02, "statusConferencia")).containsExactly("isento");
        // Nenhum cenário grava variável fora da lista branca, em nenhuma das duas tabelas.
        for (String tabela : List.of("ACT_HI_DETAIL", "ACT_HI_VARINST")) {
            List<String> nomes = jdbc.queryForList("SELECT DISTINCT TO_CHAR(NAME_) FROM SIGA2_CAMUNDA." + tabela
                    + " WHERE TO_CHAR(PROC_INST_ID_) LIKE 'fict-8%'", new MapSqlParameterSource(), String.class);
            assertThat(nomes).as(tabela).isNotEmpty().isSubsetOf(VARIAVEIS_PERMITIDAS);
        }
    }

    @Test
    void complementacaoDoHc14EInstanciaIndependenteJaConcluida() {
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST
                WHERE  TO_CHAR(BUSINESS_KEY_) = TO_CHAR(:c) AND TO_CHAR(PROC_DEF_KEY_) = :chave
                  AND  END_TIME_ IS NOT NULL AND SUPER_PROCESS_INSTANCE_ID_ IS NULL""",
                "c", COMPLEMENTACAO_DO_HC14, "chave", CHAVE_COMPLEMENTACAO)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.COMPLEMENTACAO WHERE ID = :c AND ID_REQUERIMENTO = :r",
                "c", COMPLEMENTACAO_DO_HC14, "r", HC14)).isEqualTo(1);
        // Aconteceu durante a análise: depois do início do ciclo.
        LocalDateTime inicioDaComplementacao = jdbc.queryForObject("""
                SELECT DATA_INICIO FROM SIGA2_LICENCIAMENTO.COMPLEMENTACAO WHERE ID = :c""",
                new MapSqlParameterSource("c", COMPLEMENTACAO_DO_HC14), Timestamp.class).toLocalDateTime();
        LocalDateTime inicioDaAnalise = jdbc.queryForObject("""
                SELECT START_TIME_ FROM SIGA2_CAMUNDA.ACT_HI_ACTINST WHERE PROC_INST_ID_ = :pi AND TO_CHAR(ACT_ID_) = :a""",
                new MapSqlParameterSource().addValue("pi", pi(HC14)).addValue("a", ANALISE + CORPO), Timestamp.class)
                .toLocalDateTime();
        assertThat(inicioDaComplementacao).isAfter(inicioDaAnalise);
    }

    // --- plano B

    @Test
    void planoBNaoTemInstanciaENaoTemRastroNoCamunda() {
        for (String codigo : List.of("PB01", "PB02", "PB03")) {
            assertThat(cenarios.get(codigo).temInstancia()).as(codigo).isFalse();
            assertThat(contar("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST WHERE TO_CHAR(BUSINESS_KEY_) = TO_CHAR(:id)",
                    "id", id(codigo))).as(codigo).isZero();
        }
    }

    @Test
    void planoBGravaOsEventosDoLicenciamentoEmOrdemEAsDatasDoRequerimento() {
        List<String> pb01 = jdbc.queryForList("""
                SELECT TO_CHAR(EVENTO) FROM SIGA2_LICENCIAMENTO.HISTORICO_REQUERIMENTO
                WHERE  ID_REQUERIMENTO = :id ORDER BY DATA_CADASTRO, ID""", new MapSqlParameterSource("id", PB01), String.class);
        assertThat(pb01).containsExactly("CRIACAO_REQUERIMENTO", "AGUARDANDO_CONFERENCIA", "CHECKLIST_APROVADO",
                "AGUARDANDO_PAGAMENTO_TAXA", "PAGAMENTO_CONFIRMADO", "PROTOCOLO", "EM_ANALISE", "VALIDAR_PARECER_TECNICO",
                "TITULOS_EMITIDOS", "TITULACOES_DISPONIVEIS", "CONCLUSAO_REQUERIMENTO");
        List<String> pb02 = jdbc.queryForList("""
                SELECT TO_CHAR(EVENTO) FROM SIGA2_LICENCIAMENTO.HISTORICO_REQUERIMENTO
                WHERE  ID_REQUERIMENTO = :id ORDER BY DATA_CADASTRO, ID""", new MapSqlParameterSource("id", PB02), String.class);
        assertThat(pb02).containsExactly("CRIACAO_REQUERIMENTO", "AGUARDANDO_CONFERENCIA", "EVENTO_FICTICIO_DESCONHECIDO",
                "CHECKLIST_REPROVADO");
        assertThat(contar("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.HISTORICO_REQUERIMENTO WHERE ID_REQUERIMENTO = :id",
                "id", PB03)).isZero();

        Map<String, Object> r1 = jdbc.queryForMap("""
                SELECT DATA_ABERTURA, DATA_PROTOCOLO_PROCESSO, DATA_CANCELAMENTO, DATA_EXPIRACAO_REQUERIMENTO,
                       TO_CHAR(SITUACAO_PROCESSO) AS SITUACAO, TO_CHAR(NUMERO) AS NUMERO
                FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = :id""", new MapSqlParameterSource("id", PB01));
        assertThat(((Timestamp) r1.get("DATA_ABERTURA")).toLocalDateTime().getYear()).isEqualTo(2021);
        assertThat(r1.get("DATA_PROTOCOLO_PROCESSO")).isNotNull();
        assertThat(r1.get("DATA_CANCELAMENTO")).isNull();
        assertThat(r1.get("SITUACAO")).isEqualTo("CONCLUIDO");
        assertThat(r1.get("NUMERO")).isEqualTo(PB01 + "/2021");
        Map<String, Object> r2 = jdbc.queryForMap("""
                SELECT DATA_EXPIRACAO_REQUERIMENTO, DATA_PROTOCOLO_PROCESSO FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = :id""",
                new MapSqlParameterSource("id", PB02));
        assertThat(r2.get("DATA_EXPIRACAO_REQUERIMENTO")).isNotNull();
        assertThat(r2.get("DATA_PROTOCOLO_PROCESSO")).isNull();
    }

    @Test
    void cpfUsuarioDoHistoricoDoPlanoBEFicticioEInvalido() {
        List<String> cpfs = jdbc.queryForList("""
                SELECT DISTINCT TO_CHAR(CPF_USUARIO) FROM SIGA2_LICENCIAMENTO.HISTORICO_REQUERIMENTO
                WHERE  ID_REQUERIMENTO BETWEEN 8000 AND 8199""", new MapSqlParameterSource(), String.class);
        assertThat(cpfs).containsExactly(CamundaFicticioBuilder.cpfDoServidor(3));
    }
}
