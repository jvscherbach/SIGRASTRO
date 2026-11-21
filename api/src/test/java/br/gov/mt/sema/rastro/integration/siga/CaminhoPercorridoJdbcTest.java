package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.CHAVE_COMPLEMENTACAO;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.REPETICAO_POR_ERRO_FICTICIA;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.REPORT_KAFKA_FICTICIO;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.cpfDoServidor;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.dias;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.AtividadeBruta;
import br.gov.mt.sema.rastro.tramitacao.domain.AtividadeBruta.MotivoDaTarefa;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoBruto;
import br.gov.mt.sema.rastro.tramitacao.domain.InstanciaDoHistorico;
import br.gov.mt.sema.rastro.tramitacao.domain.OrigemDaInstancia;
import br.gov.mt.sema.rastro.tramitacao.domain.VariavelBruta;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Caminho percorrido lido do Camunda fictício (S8, bloco 2), pelo usuário de leitura (só {@code SELECT}), com as fixtures
 * do bloco 1: ordem estável, filhas e complementação, ruído <b>não</b> removido, tarefas {@code deleted}, limite por
 * instância e marca de truncado, requerimento sem instância, duas instâncias raiz, variáveis da lista branca, número de
 * consultas e ausência de CPF na saída.
 */
@Testcontainers(disabledWithoutDocker = true)
class CaminhoPercorridoJdbcTest {

    /** Cenário próprio deste teste (a S8 usa a partir de 8200). */
    private static final long DUAS_INSTANCIAS = 8200;
    private static final String HIDRICO = "procOutorgaAguaSupSub";
    private static final String LAC = "procLicenciamentoLac";
    private static final Pattern CPF_SOLTO = Pattern.compile("(?<![0-9])[0-9]{11}(?![0-9])");
    private static final Pattern CONTADOR = Pattern.compile("-a(\\d+)$");

    private static final AtomicInteger CONSULTAS = new AtomicInteger();
    private static HikariDataSource leitura;
    private static NamedParameterJdbcTemplate jdbc;
    private static CamundaPosicaoJdbc camunda;

    @BeforeAll
    static void abrir() {
        assertThat(SigaOracleTeste.CENARIOS_S8).isNotEmpty();
        criarCenarioProprio();
        leitura = SigaOracleTeste.dataSourceLeitura();
        jdbc = new NamedParameterJdbcTemplate(leitura);
        camunda = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(contando(leitura)), 10);
    }

    @AfterAll
    static void fechar() {
        leitura.close();
    }

    private static void criarCenarioProprio() {
        try (HikariDataSource admin = SigaOracleTeste.dataSourceAdministrador()) {
            NamedParameterJdbcTemplate escrita = new NamedParameterJdbcTemplate(admin);
            Integer existe = escrita.queryForObject("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = :id",
                    new MapSqlParameterSource("id", DUAS_INSTANCIAS), Integer.class);
            if (existe != null && existe > 0) {
                return;
            }
            new CamundaFicticioBuilder(escrita, CatalogoDeEtapasDeTeste.padrao())
                    .processo(Fluxo.HIDRICO, DUAS_INSTANCIAS).situacao("EM_CONFERENCIA")
                    .naEtapa("taskConferirDocumentosApresentados").paraGrupo("grpSurhConferirDocumentoSuperficial")
                    .desde(dias(0)).gravar();
            // Instância anterior do mesmo requerimento, já encerrada e com uma atividade: não pode aparecer na leitura.
            escrita.update("""
                    INSERT INTO SIGA2_CAMUNDA.ACT_HI_PROCINST
                           (ID_, PROC_INST_ID_, BUSINESS_KEY_, PROC_DEF_KEY_, PROC_DEF_ID_, START_TIME_, END_TIME_, END_ACT_ID_,
                            SUPER_PROCESS_INSTANCE_ID_, ROOT_PROC_INST_ID_, DELETE_REASON_, STATE_)
                    VALUES ('fict-8200-anterior', 'fict-8200-anterior', '8200', 'procOutorgaAguaSupSub',
                            'procOutorgaAguaSupSub:1:ficticio', :inicio, :fim, 'Event_1kq2k0z', NULL, 'fict-8200-anterior',
                            NULL, 'COMPLETED')""",
                    new MapSqlParameterSource().addValue("inicio", Timestamp.valueOf(LocalDateTime.of(2025, 1, 6, 9, 0)))
                            .addValue("fim", Timestamp.valueOf(LocalDateTime.of(2025, 2, 6, 9, 0))));
            escrita.update("""
                    INSERT INTO SIGA2_CAMUNDA.ACT_HI_ACTINST
                           (ID_, PROC_DEF_KEY_, PROC_DEF_ID_, ROOT_PROC_INST_ID_, PROC_INST_ID_, EXECUTION_ID_, ACT_ID_,
                            ACT_TYPE_, START_TIME_, END_TIME_, SEQUENCE_COUNTER_)
                    VALUES ('fict-8200-anterior-a000001', 'procOutorgaAguaSupSub', 'procOutorgaAguaSupSub:1:ficticio',
                            'fict-8200-anterior', 'fict-8200-anterior', 'fict-8200-anterior', 'taskCadastrarRequerimento',
                            'serviceTask', :inicio, :fim, 1)""",
                    new MapSqlParameterSource().addValue("inicio", Timestamp.valueOf(LocalDateTime.of(2025, 1, 6, 9, 0)))
                            .addValue("fim", Timestamp.valueOf(LocalDateTime.of(2025, 1, 6, 10, 0))));
        }
    }

    private static CaminhoBruto caminho(long id) {
        return camunda.lerCaminho(id, HIDRICO);
    }

    private static CaminhoBruto caminhoLac(long id) {
        return camunda.lerCaminho(id, LAC);
    }

    private static List<AtividadeBruta> da(CaminhoBruto c, String instancia) {
        return c.atividades().stream().filter(a -> a.instancia().equals(instancia)).toList();
    }

    private static List<String> ids(List<AtividadeBruta> atividades) {
        return atividades.stream().map(AtividadeBruta::idTecnico).toList();
    }

    private static int contarNoBanco(String instancia) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_ACTINST WHERE PROC_INST_ID_ = :i",
                new MapSqlParameterSource("i", instancia), Integer.class);
        return n == null ? 0 : n;
    }

    // --- ordem, raiz, filhas e complementação

    @Test
    void ordem_e_a_de_gravacao_inicio_e_id_e_o_contador_so_cresce_dentro_da_instancia() {
        CaminhoBruto c = caminho(HC01);
        assertThat(c.atividades()).isSortedAccordingTo(
                Comparator.comparing(AtividadeBruta::inicio).thenComparing(AtividadeBruta::id));
        // Empates de início (o corpo do ciclo e as tarefas nascem juntos) saem na ordem em que foram gravados.
        List<AtividadeBruta> daRaiz = da(c, "fict-" + HC01);
        long anterior = -1;
        for (AtividadeBruta a : daRaiz) {
            var m = CONTADOR.matcher(a.id());
            assertThat(m.find()).as(a.id()).isTrue();
            long atual = Long.parseLong(m.group(1));
            assertThat(atual).as("contador de " + a.id()).isGreaterThan(anterior);
            anterior = atual;
        }
        assertThat(c.atividades()).isEqualTo(caminho(HC01).atividades());
    }

    @Test
    void feliz_traz_a_raiz_as_duas_filhas_da_emissao_e_o_que_ficou_aberto() {
        CaminhoBruto c = caminho(HC01);
        assertThat(c.instanciasRaiz()).isEqualTo(1);
        assertThat(c.raiz()).hasValueSatisfying(r -> {
            assertThat(r.id()).isEqualTo("fict-" + HC01);
            assertThat(r.chave()).isEqualTo(HIDRICO);
            assertThat(r.encerrada()).isFalse();
            assertThat(r.truncada()).isFalse();
            assertThat(r.lidas()).isEqualTo(contarNoBanco(r.id()));
        });
        assertThat(c.instancias().getFirst().origem()).isEqualTo(OrigemDaInstancia.RAIZ);
        List<InstanciaDoHistorico> filhas = c.instancias().stream().filter(i -> i.origem() == OrigemDaInstancia.FILHA)
                .toList();
        assertThat(filhas).extracting(InstanciaDoHistorico::chave)
                .containsExactlyInAnyOrder("procSurhEmitirAutorizacoes", "procSurhEmissaoPortaria");
        assertThat(filhas).allSatisfy(f -> {
            assertThat(f.idDaMae()).isEqualTo("fict-" + HC01);
            assertThat(f.encerrada()).isTrue();
            assertThat(f.lidas()).isEqualTo(contarNoBanco(f.id())).isPositive();
        });
        // A callActivity da raiz aponta para a filha que chamou; as tarefas da filha vêm com a filha.
        List<String> chamadas = da(c, "fict-" + HC01).stream().map(AtividadeBruta::chamada)
                .filter(x -> x != null).toList();
        assertThat(chamadas).containsExactlyInAnyOrderElementsOf(filhas.stream().map(InstanciaDoHistorico::id).toList());
        // Só a verificação da revisão ficou aberta na raiz (regra 9).
        assertThat(da(c, "fict-" + HC01).stream().filter(AtividadeBruta::aberta).map(AtividadeBruta::idTecnico))
                .containsExactly(VERIFICACAO);
    }

    @Test
    void horarios_e_responsavel_vem_como_gravados_e_o_cpf_vem_protegido() {
        CaminhoBruto c = caminho(HC01);
        AtividadeBruta cadastro = da(c, "fict-" + HC01).stream().filter(a -> a.idTecnico().equals(CADASTRO)).findFirst()
                .orElseThrow();
        assertThat(cadastro.inicio()).isEqualTo(dias(0));
        assertThat(cadastro.fim()).isEqualTo(dias(1));
        assertThat(cadastro.tarefa()).hasValueSatisfying(t -> assertThat(t.motivo()).isEqualTo(MotivoDaTarefa.CONCLUIDA));
        List<String> analistas = da(c, "fict-" + HC01).stream()
                .filter(a -> a.idTecnico().equals(ANALISE) && a.tarefa().isPresent())
                .map(a -> a.responsavel().orElseThrow().valorProtegido()).sorted().toList();
        assertThat(analistas).containsExactly(cpfDoServidor(5), cpfDoServidor(6), cpfDoServidor(7));
        // O corpo da multi-instância é uma linha própria, sem tarefa.
        assertThat(da(c, "fict-" + HC01).stream().filter(a -> a.tipo().equals("multiInstanceBody")).map(AtividadeBruta::idTecnico))
                .contains(ANALISE + "#multiInstanceBody");
    }

    @Test
    void complementacao_concluida_vem_como_instancia_propria() {
        CaminhoBruto c = caminho(HC14);
        List<InstanciaDoHistorico> compl = c.instancias().stream()
                .filter(i -> i.origem() == OrigemDaInstancia.COMPLEMENTACAO).toList();
        assertThat(compl).hasSize(1);
        assertThat(compl.getFirst().chave()).isEqualTo(CHAVE_COMPLEMENTACAO);
        assertThat(compl.getFirst().encerrada()).isTrue();
        assertThat(compl.getFirst().idDaMae()).isNull();
        assertThat(da(c, compl.getFirst().id())).isNotEmpty().allSatisfy(a -> {
            assertThat(a.origem()).isEqualTo(OrigemDaInstancia.COMPLEMENTACAO);
            assertThat(a.tarefa()).hasValueSatisfying(t -> assertThat(t.motivo()).isEqualTo(MotivoDaTarefa.CONCLUIDA));
        });
        // A raiz continua sendo a primeira e a única raiz.
        assertThat(c.instancias().getFirst().origem()).isEqualTo(OrigemDaInstancia.RAIZ);
        assertThat(c.instancias().stream().filter(i -> i.origem() == OrigemDaInstancia.RAIZ)).hasSize(1);
    }

    @Test
    void emissao_com_tres_filhas_e_uma_devolvida_traz_as_seis_filhas_e_o_estado_da_devolvida() {
        CaminhoBruto c = caminho(HC12);
        List<InstanciaDoHistorico> filhas = c.instancias().stream().filter(i -> i.origem() == OrigemDaInstancia.FILHA)
                .toList();
        assertThat(filhas).hasSize(6);
        assertThat(filhas.stream().filter(f -> "INTERNALLY_TERMINATED".equals(f.estado()))).hasSize(1);
        // A tarefa da filha devolvida foi encerrada sem ação.
        InstanciaDoHistorico devolvida = filhas.stream().filter(f -> "INTERNALLY_TERMINATED".equals(f.estado()))
                .findFirst().orElseThrow();
        assertThat(da(c, devolvida.id())).anySatisfy(
                a -> assertThat(a.tarefa()).hasValueSatisfying(t -> assertThat(t.motivo()).isEqualTo(MotivoDaTarefa.EXCLUIDA)));
    }

    // --- ruído e tarefas encerradas sem ação: nada é removido aqui

    @Test
    void ruido_nao_e_removido_nem_colapsado() {
        List<String> ids = ids(da(caminho(HC11), "fict-" + HC11));
        assertThat(ids).contains("Activity_0miwhxt", REPORT_KAFKA_FICTICIO, REPETICAO_POR_ERRO_FICTICIA);
        assertThat(ids.stream().filter(EMITIR_TAXA::equals)).hasSize(4);
    }

    @Test
    void tarefas_deleted_vem_marcadas_como_excluidas_e_as_concluidas_como_concluidas() {
        List<AtividadeBruta> analise = da(caminho(HC10), "fict-" + HC10).stream()
                .filter(a -> a.idTecnico().equals(ANALISE) && a.tarefa().isPresent()).toList();
        assertThat(analise).hasSize(3);
        assertThat(analise.stream().filter(a -> a.tarefa().get().motivo() == MotivoDaTarefa.CONCLUIDA)).hasSize(1);
        assertThat(analise.stream().filter(a -> a.tarefa().get().motivo() == MotivoDaTarefa.EXCLUIDA)).hasSize(2);
        // A tarefa aberta (correção do interessado) vem como aberta.
        assertThat(da(caminho(HC10), "fict-" + HC10).stream().filter(AtividadeBruta::aberta)
                .filter(a -> a.tarefa().isPresent()))
                .allSatisfy(a -> assertThat(a.tarefa().get().motivo()).isEqualTo(MotivoDaTarefa.ABERTA));
    }

    @Test
    void recusa_no_aceite_traz_as_duas_tarefas_deleted() {
        List<AtividadeBruta> aceite = da(caminho(HC06), "fict-" + HC06).stream()
                .filter(a -> a.idTecnico().equals(ACEITE) && a.tarefa().isPresent()).toList();
        assertThat(aceite.stream().filter(a -> a.tarefa().get().motivo() == MotivoDaTarefa.EXCLUIDA)).isNotEmpty();
        assertThat(aceite.stream().filter(a -> a.tarefa().get().motivo() == MotivoDaTarefa.CONCLUIDA)).isNotEmpty();
    }

    // --- limite por instância

    @Test
    void instancia_com_milhares_de_atividades_vem_truncada_no_limite_padrao() {
        CaminhoBruto c = caminho(HC15);
        InstanciaDoHistorico raiz = c.raiz().orElseThrow();
        assertThat(contarNoBanco(raiz.id())).isGreaterThan(3000);
        assertThat(raiz.truncada()).isTrue();
        assertThat(raiz.lidas()).isEqualTo(3000);
        assertThat(da(c, raiz.id())).hasSize(3000);
        assertThat(c.truncado()).isTrue();
        // As primeiras, na ordem de gravação: o cadastro abre o caminho.
        assertThat(da(c, raiz.id()).getFirst().idTecnico()).isEqualTo(CADASTRO);
    }

    @Test
    void limite_maior_que_a_instancia_le_tudo_sem_marcar_truncado() {
        CamundaPosicaoJdbc folgado = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(leitura), 10, 10_000);
        CaminhoBruto c = folgado.lerCaminho(HC15, HIDRICO);
        InstanciaDoHistorico raiz = c.raiz().orElseThrow();
        assertThat(raiz.truncada()).isFalse();
        assertThat(raiz.lidas()).isEqualTo(contarNoBanco(raiz.id()));
        assertThat(c.truncado()).isFalse();
    }

    @Test
    void limite_pequeno_vale_por_instancia_e_so_a_instancia_grande_fica_truncada() {
        CamundaPosicaoJdbc curto = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(leitura), 10, 6);
        CaminhoBruto c = curto.lerCaminho(HC12, HIDRICO);
        for (InstanciaDoHistorico i : c.instancias()) {
            assertThat(i.lidas()).as(i.id()).isEqualTo(Math.min(6, contarNoBanco(i.id())));
            assertThat(i.truncada()).as(i.id()).isEqualTo(contarNoBanco(i.id()) > 6);
            assertThat(da(c, i.id())).as(i.id()).hasSize(i.lidas());
        }
        assertThat(c.raiz().orElseThrow().truncada()).isTrue();
    }

    // --- sem instância, duas instâncias, modelo errado

    @ParameterizedTest(name = "plano B: {0}")
    @ValueSource(longs = {PB01, PB02, PB03})
    void sem_instancia_devolve_vazio_com_uma_so_consulta(long id) {
        CONSULTAS.set(0);
        CaminhoBruto c = caminho(id);
        assertThat(c.vazio()).isTrue();
        assertThat(c.raiz()).isEmpty();
        assertThat(c.atividades()).isEmpty();
        assertThat(c.instanciasRaiz()).isZero();
        assertThat(CONSULTAS.get()).isEqualTo(1);
    }

    @Test
    void duas_instancias_raiz_valem_a_mais_recente_e_nada_da_anterior_entra() {
        CaminhoBruto c = caminho(DUAS_INSTANCIAS);
        assertThat(c.instanciasRaiz()).isEqualTo(2);
        assertThat(c.raiz().orElseThrow().id()).isEqualTo("fict-" + DUAS_INSTANCIAS);
        assertThat(c.atividades()).isNotEmpty().allSatisfy(a -> assertThat(a.instancia()).doesNotContain("anterior"));
    }

    @Test
    void chave_do_modelo_sempre_filtra() {
        assertThat(caminhoLac(HC01).vazio()).isTrue();
        assertThat(caminho(LC01).vazio()).isTrue();
        assertThat(caminhoLac(LC01).vazio()).isFalse();
    }

    // --- variáveis da lista branca

    @Test
    void variaveis_da_epoca_vem_em_ordem_e_as_finais_so_com_o_ultimo_valor() {
        CaminhoBruto c = caminho(HC04);
        assertThat(c.variaveisDaEpoca().stream().filter(v -> v.nome().equals("decisaoValidacaoParecerTecnico"))
                .map(v -> v.valor().orElseThrow())).containsExactly("RA", "RP");
        assertThat(c.variaveisFinais().stream().filter(v -> v.nome().equals("decisaoValidacaoParecerTecnico"))
                .map(v -> v.valor().orElseThrow())).containsExactly("RP");
        CaminhoBruto reprovado = caminho(HC03);
        assertThat(reprovado.variaveisDaEpoca().stream().map(v -> v.valor().orElseThrow()))
                .containsSubsequence("reprovado", "taxavel");
        assertThat(reprovado.variaveisFinais().stream().map(v -> v.valor().orElseThrow())).contains("taxavel")
                .doesNotContain("reprovado");
    }

    @Test
    void so_a_lista_branca_e_lida_e_a_do_construtor_e_a_mesma() {
        assertThat(VariavelBruta.PERMITIDAS).isEqualTo(CamundaFicticioBuilder.VARIAVEIS_PERMITIDAS);
        for (long id : new long[] {HC03, HC04, HC05, HC12, HC13, LC01}) {
            CaminhoBruto c = id == LC01 ? caminhoLac(id) : caminho(id);
            assertThat(c.variaveisDaEpoca()).isNotEmpty()
                    .allSatisfy(v -> assertThat(VariavelBruta.PERMITIDAS).contains(v.nome()));
            assertThat(c.variaveisFinais()).allSatisfy(v -> assertThat(VariavelBruta.PERMITIDAS).contains(v.nome()));
        }
    }

    // --- número de consultas e dado pessoal

    @Test
    void numero_de_consultas_e_fixo_e_nao_depende_do_tamanho_do_caminho() {
        for (long id : new long[] {HC01, HC12, HC14, HC15}) {
            CONSULTAS.set(0);
            caminho(id);
            assertThat(CONSULTAS.get()).as("consultas de " + id).isEqualTo(CamundaPosicaoJdbc.CONSULTAS_DO_CAMINHO);
        }
    }

    @Test
    void cpf_nunca_aparece_na_representacao_do_caminho_de_nenhum_cenario() {
        for (long id : new long[] {HC01, HC02, HC03, HC04, HC05, HC06, HC07, HC08, HC09, HC10, HC11, HC12, HC13, HC14,
                HF01, HF02, HF03, HF04}) {
            assertThat(CPF_SOLTO.matcher(caminho(id).toString()).find()).as("HC/HF " + id).isFalse();
        }
        for (long id : new long[] {LC01, LC02, LC03, LF01, LF02, LF03, LF04, LF05}) {
            assertThat(CPF_SOLTO.matcher(caminhoLac(id).toString()).find()).as("LC/LF " + id).isFalse();
        }
    }

    @Test
    void finais_trazem_a_atividade_final_da_instancia_encerrada() {
        InstanciaDoHistorico raiz = caminho(HF04).raiz().orElseThrow();
        assertThat(raiz.encerrada()).isTrue();
        assertThat(raiz.atividadeFinal()).isEqualTo("Event_0h1oige");
        assertThat(caminhoLac(LF03).raiz().orElseThrow().atividadeFinal())
                .isEqualTo("evtEncerrarProcessoPrazoCorrecaoExpirado");
    }

    // --- contagem de comandos preparados

    private static DataSource contando(DataSource alvo) {
        return (DataSource) Proxy.newProxyInstance(CaminhoPercorridoJdbcTest.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, metodo, args) -> {
                    Object resultado = invocar(alvo, metodo, args);
                    if (resultado instanceof Connection conexao) {
                        return Proxy.newProxyInstance(CaminhoPercorridoJdbcTest.class.getClassLoader(),
                                new Class<?>[] {Connection.class}, (p2, m2, a2) -> {
                                    if (m2.getName().startsWith("prepare") || m2.getName().equals("createStatement")) {
                                        CONSULTAS.incrementAndGet();
                                    }
                                    return invocar(conexao, m2, a2);
                                });
                    }
                    return resultado;
                });
    }

    private static Object invocar(Object alvo, java.lang.reflect.Method metodo, Object[] args) throws Throwable {
        try {
            return metodo.invoke(alvo, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
