package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.*;
import static org.assertj.core.api.Assertions.assertThat;

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
 * O construtor grava exatamente o que cada cenário da S7 descreve (tarefas, filhas, assinaturas, finais). Lê pelo usuário
 * de leitura, o que também prova os grants das tabelas novas. As consultas daqui são de conferência das fixtures, não o SQL
 * de produção (bloco 3).
 */
@Testcontainers(disabledWithoutDocker = true)
class CamundaFicticioBuilderTest {

    private static HikariDataSource dataSource;
    private static NamedParameterJdbcTemplate jdbc;
    private static Map<String, Resultado> cenarios;
    private static final CatalogoDeEtapas CATALOGO = CatalogoDeEtapasDeTeste.padrao();

    @BeforeAll
    static void abrir() {
        cenarios = SigaOracleTeste.CENARIOS_S7;
        dataSource = SigaOracleTeste.dataSourceLeitura();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
    }

    private static String pi(long id) {
        return "fict-" + id;
    }

    private static int contar(String sql, Object... pares) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        for (int i = 0; i < pares.length; i += 2) {
            p.addValue((String) pares[i], pares[i + 1]);
        }
        Integer n = jdbc.queryForObject(sql, p, Integer.class);
        return n == null ? 0 : n;
    }

    private static List<String> chavesDasTarefas(long id) {
        return jdbc.queryForList("""
                SELECT TO_CHAR(TASK_DEF_KEY_) FROM SIGA2_CAMUNDA.ACT_RU_TASK
                WHERE  PROC_INST_ID_ = :pi ORDER BY TASK_DEF_KEY_""",
                new MapSqlParameterSource("pi", pi(id)), String.class);
    }

    private static List<String> assigneesDasTarefas(long id) {
        return jdbc.queryForList("""
                SELECT TO_CHAR(ASSIGNEE_) FROM SIGA2_CAMUNDA.ACT_RU_TASK
                WHERE  PROC_INST_ID_ = :pi ORDER BY ASSIGNEE_""",
                new MapSqlParameterSource("pi", pi(id)), String.class);
    }

    private static List<String> gruposDasTarefas(long id) {
        return jdbc.queryForList("""
                SELECT TO_CHAR(l.GROUP_ID_) FROM SIGA2_CAMUNDA.ACT_RU_IDENTITYLINK l
                JOIN   SIGA2_CAMUNDA.ACT_RU_TASK t ON TO_CHAR(t.ID_) = TO_CHAR(l.TASK_ID_)
                WHERE  t.PROC_INST_ID_ = :pi AND l.TYPE_ = 'candidate'""",
                new MapSqlParameterSource("pi", pi(id)), String.class);
    }

    private static int assinaturas(long id) {
        return contar("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_EVENT_SUBSCR WHERE PROC_INST_ID_ = :pi", "pi", pi(id));
    }

    private static String atividadeDaExecucao(long id) {
        return jdbc.queryForObject("""
                SELECT TO_CHAR(MAX(ACT_ID_)) FROM SIGA2_CAMUNDA.ACT_RU_EXECUTION
                WHERE  PROC_INST_ID_ = :pi AND ACT_ID_ IS NOT NULL""",
                new MapSqlParameterSource("pi", pi(id)), String.class);
    }

    private static Map<String, Object> instanciaHistorica(long id, String chave) {
        return jdbc.queryForMap("""
                SELECT TO_CHAR(ID_) AS ID, TO_CHAR(END_ACT_ID_) AS FIM, END_TIME_ AS TERMINO, TO_CHAR(STATE_) AS ESTADO
                FROM   SIGA2_CAMUNDA.ACT_HI_PROCINST
                WHERE  TO_CHAR(BUSINESS_KEY_) = TO_CHAR(:id) AND TO_CHAR(PROC_DEF_KEY_) = :chave""",
                new MapSqlParameterSource().addValue("id", id).addValue("chave", chave));
    }

    // --- regras gerais

    @Test
    void todosOsCenariosObrigatoriosForamGravados() {
        assertThat(cenarios.keySet()).containsExactlyInAnyOrder(
                "H01", "H02", "H03", "H04", "H05", "H06", "H07", "H08", "H09", "H10", "H11", "H12", "H13", "H14", "H15",
                "H16", "H17", "H18", "H19", "H20", "H21", "L01", "L02", "L03", "L04", "L05", "L06", "L07", "TRIFASICO",
                "G01", "G02", "G03");
        assertThat(contar("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID BETWEEN 7000 AND 7199"))
                .isEqualTo(cenarios.size());
    }

    @Test
    void cadaInstanciaTemUmaExecucaoRaizComABusinessKeyDoRequerimento() {
        for (Resultado r : cenarios.values()) {
            if (!r.temInstancia() || r.encerrada()) {
                continue;
            }
            assertThat(contar("""
                    SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_EXECUTION
                    WHERE  PARENT_ID_ IS NULL AND ID_ = PROC_INST_ID_ AND BUSINESS_KEY_ = TO_CHAR(:id)
                      AND  ID_ = :pi""", "id", r.idRequerimento(), "pi", r.instancia()))
                    .as("raiz de %d", r.idRequerimento()).isEqualTo(1);
            // Execuções filhas (concorrentes, corpo da multi-instância) não levam business key.
            assertThat(contar("""
                    SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_EXECUTION
                    WHERE  PROC_INST_ID_ = :pi AND PARENT_ID_ IS NOT NULL AND BUSINESS_KEY_ IS NOT NULL""",
                    "pi", r.instancia())).isZero();
        }
    }

    @Test
    void todaAtividadeAbertaDaRaizEstaNoCatalogo() {
        for (Resultado r : cenarios.values()) {
            if (!r.temInstancia() || r.encerrada()) {
                continue;
            }
            String chave = jdbc.queryForObject(
                    "SELECT TO_CHAR(PROC_DEF_KEY_) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST WHERE ID_ = :pi",
                    new MapSqlParameterSource("pi", r.instancia()), String.class);
            ModeloDeFluxo modelo = CATALOGO.modelo(chave).orElseThrow();
            List<String> abertas = jdbc.queryForList("""
                    SELECT TO_CHAR(ACT_ID_) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST
                    WHERE  PROC_INST_ID_ = :pi AND END_TIME_ IS NULL AND TO_CHAR(ACT_TYPE_) <> 'multiInstanceBody'""",
                    new MapSqlParameterSource("pi", r.instancia()), String.class);
            assertThat(abertas).as("abertas de %d", r.idRequerimento()).isNotEmpty();
            for (String atividade : abertas) {
                assertThat(modelo.resolver(atividade, Set.of()))
                        .as("%s de %d", atividade, r.idRequerimento())
                        .isNotInstanceOf(ResolucaoDeEtapa.NaoMapeado.class);
            }
        }
    }

    // --- Hídrico

    @Test
    void h01TarefaDoInteressadoNoCadastro() {
        assertThat(chavesDasTarefas(H01)).containsExactly("taskCadastrarRequerimento");
        assertThat(assigneesDasTarefas(H01)).containsExactly(CamundaFicticioBuilder.CPF_DO_INTERESSADO);
        assertThat(gruposDasTarefas(H01)).isEmpty();
    }

    @Test
    void h02ConferenciaComGrupo() {
        assertThat(chavesDasTarefas(H02)).containsExactly("taskConferirDocumentosApresentados");
        assertThat(gruposDasTarefas(H02)).containsExactly("grpSurhConferirDocumentoSubterranea");
        assertThat(assigneesDasTarefas(H02)).containsOnlyNulls();
    }

    @Test
    void h03AguardandoPagamentoEhGatewayComAssinaturaENaoTarefa() {
        assertThat(chavesDasTarefas(H03)).isEmpty();
        assertThat(atividadeDaExecucao(H03)).isEqualTo("gtwAguardarPagamento");
        assertThat(assinaturas(H03)).isEqualTo(1);
        assertThat(cenarios.get("H03").assinaturasDeEvento()).isEqualTo(1);
    }

    @Test
    void h04IsentoSemPassagemPeloGatewayDePagamento() {
        assertThat(atividadeDaExecucao(H04)).isEqualTo("taskGerarNumeroProtocolo");
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST
                WHERE  PROC_INST_ID_ = :pi AND TO_CHAR(ACT_ID_) = 'gtwAguardarPagamento'""", "pi", pi(H04))).isZero();
        assertThat(chavesDasTarefas(H04)).isEmpty();
    }

    @Test
    void h05AtribuicaoComGrupo() {
        assertThat(chavesDasTarefas(H05)).containsExactly("taskAtribuirRequerimento");
        assertThat(gruposDasTarefas(H05)).containsExactly("grpSurhAtribuirRequerimentoSuperficial");
    }

    @Test
    void h06AceiteComDoisDeTresConcluidos() {
        assertThat(chavesDasTarefas(H06)).containsExactly("taskAceitarAtribuicao");
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_TASKINST
                WHERE  PROC_INST_ID_ = :pi AND TO_CHAR(TASK_DEF_KEY_) = 'taskAceitarAtribuicao'
                  AND  END_TIME_ IS NOT NULL AND TO_CHAR(DELETE_REASON_) = 'completed'""", "pi", pi(H06))).isEqualTo(2);
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_TASKINST
                WHERE  PROC_INST_ID_ = :pi AND TO_CHAR(TASK_DEF_KEY_) = 'taskAceitarAtribuicao'""", "pi", pi(H06)))
                .isEqualTo(3);
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST
                WHERE  PROC_INST_ID_ = :pi AND TO_CHAR(ACT_TYPE_) = 'multiInstanceBody' AND END_TIME_ IS NULL""",
                "pi", pi(H06))).isEqualTo(1);
        assertThat(cenarios.get("H06").tarefasConcluidasNoCiclo()).isEqualTo(2);
    }

    @Test
    void h07AnaliseComTresAnalistas() {
        assertThat(chavesDasTarefas(H07)).hasSize(3).containsOnly("taskAnalisarRequerimento");
        assertThat(assigneesDasTarefas(H07)).containsExactly(CamundaFicticioBuilder.cpfDoServidor(1),
                CamundaFicticioBuilder.cpfDoServidor(2), CamundaFicticioBuilder.cpfDoServidor(3));
        assertThat(cenarios.get("H07").tarefasAtivasNaRaiz()).isEqualTo(3);
    }

    @Test
    void h08RevisaoComAChefia() {
        assertThat(chavesDasTarefas(H08)).containsExactly("taskRevisarParecerTecnico");
        assertThat(assigneesDasTarefas(H08)).containsExactly(CamundaFicticioBuilder.cpfDoServidor(4));
    }

    @Test
    void h09ValidacaoComTresSignatarios() {
        assertThat(chavesDasTarefas(H09)).hasSize(3).containsOnly("taskValidarParecerTecnico");
        assertThat(assigneesDasTarefas(H09)).containsExactly(CamundaFicticioBuilder.cpfDoServidor(5),
                CamundaFicticioBuilder.cpfDoServidor(6), CamundaFicticioBuilder.cpfDoServidor(7));
    }

    @Test
    void h10DuasFilhasEmParaleloLigadasPelaInstanciaMae() {
        assertThat(chavesDasTarefas(H10)).isEmpty();
        List<String> filhas = jdbc.queryForList("""
                SELECT TO_CHAR(PROC_DEF_KEY_) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST
                WHERE  TO_CHAR(SUPER_PROCESS_INSTANCE_ID_) = :pi AND END_TIME_ IS NULL ORDER BY 1""",
                new MapSqlParameterSource("pi", pi(H10)), String.class);
        assertThat(filhas).containsExactly("procSurhEmissaoPortaria", "procSurhEmitirAutorizacoes");
        // A business key da filha é o id do título, nunca o do requerimento.
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST
                WHERE  TO_CHAR(SUPER_PROCESS_INSTANCE_ID_) = :pi AND TO_CHAR(BUSINESS_KEY_) = TO_CHAR(:id)""",
                "pi", pi(H10), "id", H10)).isZero();
        // Na execução, a filha aponta para a execução da callActivity da mãe (SUPER_EXEC_).
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_EXECUTION f
                JOIN   SIGA2_CAMUNDA.ACT_RU_EXECUTION m ON TO_CHAR(m.ID_) = TO_CHAR(f.SUPER_EXEC_)
                WHERE  m.PROC_INST_ID_ = :pi AND f.PARENT_ID_ IS NULL""", "pi", pi(H10))).isEqualTo(2);
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_TASK
                WHERE  PROC_INST_ID_ IN (:f1, :f2)""", "f1", pi(H10) + "-filha-1", "f2", pi(H10) + "-filha-2"))
                .isEqualTo(2);
        assertThat(cenarios.get("H10").filhas()).isEqualTo(2);
    }

    @Test
    void h11RegistroDaPublicacaoComGrupo() {
        assertThat(chavesDasTarefas(H11)).containsExactly("taskRegistarComprovantePublicacao");
        assertThat(gruposDasTarefas(H11)).containsExactly("grpSurhRegistrarPublicacaoBarramento");
    }

    @Test
    void h12VerificacaoDaRevisaoComSituacaoConcluido() {
        assertThat(chavesDasTarefas(H12)).containsExactly("taskVerificarRevisaoProcesso");
        assertThat(gruposDasTarefas(H12)).containsExactly("grpSurhRevisarDecisaoMultidisciplinar");
        assertThat(situacao(H12)).isEqualTo("CONCLUIDO");
    }

    @Test
    void h13CorrecaoComOInteressado() {
        assertThat(chavesDasTarefas(H13)).containsExactly("taskCorrecaoRequerimento");
        assertThat(assigneesDasTarefas(H13)).containsExactly(CamundaFicticioBuilder.CPF_DO_INTERESSADO);
    }

    @Test
    void h14CorrecaoAguardandoPagamento() {
        assertThat(chavesDasTarefas(H14)).isEmpty();
        assertThat(atividadeDaExecucao(H14)).isEqualTo("gtwAguardarPagamentoRevisao");
        assertThat(assinaturas(H14)).isEqualTo(1);
    }

    @Test
    void h15aH18EncerradosNoFinalCertoESemExecucaoAtiva() {
        Map<Long, String> finais = Map.of(H15, "Event_1wiuc48", H16, "Event_1kq2k0z", H17, "Event_0abm55w",
                H18, "Event_0h1oige");
        finais.forEach((id, fim) -> {
            Map<String, Object> hi = instanciaHistorica(id, "procOutorgaAguaSupSub");
            assertThat(hi.get("FIM")).as("final de %d", id).isEqualTo(fim);
            assertThat(hi.get("TERMINO")).isNotNull();
            assertThat(hi.get("ESTADO")).isEqualTo("COMPLETED");
            assertThat(contar("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_RU_EXECUTION WHERE PROC_INST_ID_ = :pi",
                    "pi", pi(id))).isZero();
        });
        // Bug de origem: encerrado por taxa vencida, mas a situação continua "aguardando pagamento".
        assertThat(situacao(H18)).isEqualTo("AGUARDANDO_PAGAMENTO_DE_TAXAS");
    }

    @Test
    void h19SoATarefaSuporteAtivaEOPercursoNoHistorico() {
        assertThat(chavesDasTarefas(H19)).containsExactly("Activity_0miwhxt");
        assertThat(gruposDasTarefas(H19)).containsExactly(CamundaFicticioBuilder.GRUPO_SUPORTE);
        assertThat(jdbc.queryForList("""
                SELECT TO_CHAR(ACT_ID_) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST
                WHERE  PROC_INST_ID_ = :pi AND END_TIME_ IS NOT NULL ORDER BY START_TIME_""",
                new MapSqlParameterSource("pi", pi(H19)), String.class))
                .containsExactly("taskCadastrarRequerimento", "taskConferirDocumentosApresentados");
    }

    @Test
    void h20ComplementacaoParalelaComBusinessKeyQueColideComOH01() {
        assertThat(chavesDasTarefas(H20)).containsExactly("taskAnalisarRequerimento");
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.COMPLEMENTACAO
                WHERE  ID = :c AND ID_REQUERIMENTO = :id""", "c", COMPLEMENTACAO_DO_H20, "id", H20)).isEqualTo(1);
        // Mesma business key em dois modelos: só a chave da definição separa o requerimento H01 da complementação do H20.
        assertThat(jdbc.queryForList("""
                SELECT TO_CHAR(PROC_DEF_KEY_) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST
                WHERE  TO_CHAR(BUSINESS_KEY_) = TO_CHAR(:id) ORDER BY 1""",
                new MapSqlParameterSource("id", H01), String.class))
                .containsExactly("procGestaoComplementacao", "procOutorgaAguaSupSub");
        assertThat(cenarios.get("H20").complementacao()).isTrue();
    }

    @Test
    void h21LegadoSemInstancia() {
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST WHERE TO_CHAR(BUSINESS_KEY_) = TO_CHAR(:id)""",
                "id", H21)).isZero();
        assertThat(cenarios.get("H21").temInstancia()).isFalse();
        assertThat(contar("""
                SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE NUMERO_PROCESSO = :p""",
                "p", PROCESSO_COMPARTILHADO)).isEqualTo(3);
    }

    // --- LAC

    @Test
    void lacAtivos() {
        assertThat(chavesDasTarefas(L01)).containsExactly("taskConferirDocumentosApresentados");
        assertThat(gruposDasTarefas(L01)).containsExactly("GrpProcResponsavelAtendimentoLac");
        assertThat(atividadeDaExecucao(L02)).isEqualTo("gwtAguardarPagtoOuVencto");
        assertThat(assinaturas(L02)).isEqualTo(1);
        assertThat(chavesDasTarefas(L03)).containsExactly("taskCorrecaoRequerimento");
        assertThat(assigneesDasTarefas(L03)).containsExactly(CamundaFicticioBuilder.CPF_DO_INTERESSADO);
        assertThat(chavesDasTarefas(L04)).containsExactly("taskEnviarTermoAdesao");
    }

    @Test
    void lacEncerrados() {
        assertThat(instanciaHistorica(L05, "procLicenciamentoLac").get("FIM")).isEqualTo("evtProcessoConcluido");
        assertThat(instanciaHistorica(L06, "procLicenciamentoLac").get("FIM")).isEqualTo("Event_0x7hz1p");
        assertThat(instanciaHistorica(L07, "procLicenciamentoLac").get("FIM"))
                .isEqualTo("evtEncerrarProcessoPrazoCorrecaoExpirado");
        // Mesma situação do expirado no cadastro (regra técnica 8): o rótulo virá do final.
        assertThat(situacao(L07)).isEqualTo("CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO");
    }

    // --- fora do catálogo e fallback

    @Test
    void trifasicoNaoTemInstanciaETemInstanciaDeRequerimentoForaDoCatalogo() {
        String instancia = jdbc.queryForObject("""
                SELECT tp.INSTANCIA_REQUERIMENTO FROM SIGA2_LICENCIAMENTO.REQUERIMENTO r
                JOIN   SIGA2_LICENCIAMENTO.TIPO_PROCESSO tp ON tp.ID = r.ID_TIPO_PROCESSO
                WHERE  r.ID = :id""", new MapSqlParameterSource("id", TRIFASICO), String.class);
        assertThat(CATALOGO.modeloDaInstancia(instancia)).isEmpty();
        assertThat(CATALOGO.modeloDaInstancia(instanciaDoTipo(H01))).isPresent();
        assertThat(CATALOGO.modeloDaInstancia(instanciaDoTipo(L01))).isPresent();
        assertThat(cenarios.get("TRIFASICO").temInstancia()).isFalse();
    }

    @Test
    void gruposDeFallbackECpfSemServidor() {
        assertThat(gruposDasTarefas(G01)).containsExactly(CamundaFicticioBuilder.GRUPO_DESCONHECIDO);
        assertThat(gruposDasTarefas(G02)).containsExactly(CamundaFicticioBuilder.GRUPO_ZERO);
        assertThat(assigneesDasTarefas(G03)).containsExactly(CamundaFicticioBuilder.CPF_SEM_SERVIDOR);
        assertThat(contar("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.SERVIDOR WHERE CPF = :cpf",
                "cpf", CamundaFicticioBuilder.CPF_SEM_SERVIDOR)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.SERVIDOR WHERE ID BETWEEN 7001 AND 7012"))
                .isEqualTo(CamundaFicticioBuilder.SERVIDORES);
    }

    private static String situacao(long id) {
        return jdbc.queryForObject("SELECT SITUACAO_PROCESSO FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = :id",
                new MapSqlParameterSource("id", id), String.class);
    }

    private static String instanciaDoTipo(long id) {
        return jdbc.queryForObject("""
                SELECT tp.INSTANCIA_REQUERIMENTO FROM SIGA2_LICENCIAMENTO.REQUERIMENTO r
                JOIN   SIGA2_LICENCIAMENTO.TIPO_PROCESSO tp ON tp.ID = r.ID_TIPO_PROCESSO
                WHERE  r.ID = :id""", new MapSqlParameterSource("id", id), String.class);
    }
}
