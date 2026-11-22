package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.FALSOS_DO_FIN14;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.FIN01;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.FIN05;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.FIN06;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.FIN07;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.FIN08;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.FIN09;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.FIN10;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.FIN13;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.FIN14;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.LongStream;

import javax.sql.DataSource;

import br.gov.mt.sema.rastro.financeiro.domain.ClassificarValidade;
import br.gov.mt.sema.rastro.financeiro.domain.CodigoDoAvisoFinanceiro;
import br.gov.mt.sema.rastro.financeiro.domain.Inconsistencia;
import br.gov.mt.sema.rastro.financeiro.domain.Lancamento;
import br.gov.mt.sema.rastro.financeiro.domain.LancamentoMantido;
import br.gov.mt.sema.rastro.financeiro.domain.LancamentosClassificados;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import br.gov.mt.sema.rastro.financeiro.domain.MotivoDoIgnorado;
import br.gov.mt.sema.rastro.financeiro.domain.StatusDaGuia;
import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Leitura dos lançamentos de taxa no Oracle fictício (S9, bloco 2), com o usuário de leitura (só {@code SELECT}): FIN01,
 * FIN05 a FIN10, FIN13 e FIN14 pela regra de validade; status desconhecido e {@code PAGA} sem dados; uma consulta por
 * requerimento e uma por lote; tempo limite em toda consulta; e o SQL realmente enviado ao banco, sem as colunas proibidas.
 */
@Testcontainers(disabledWithoutDocker = true)
class LancamentosDoSigaJdbcTest {

    /** Cenários próprios deste teste (fora da faixa 9001 a 9199 da S9). */
    private static final long STATUS_DESCONHECIDO = 9201;
    private static final long PAGA_SEM_DADOS = 9202;

    private static final List<String> SQL_ENVIADO = new CopyOnWriteArrayList<>();
    private static final List<Integer> TEMPOS_LIMITE = new CopyOnWriteArrayList<>();
    private static HikariDataSource leitura;
    private static LancamentosDoSigaJdbc lancamentos;

    @BeforeAll
    static void abrir() {
        assertThat(SigaOracleTeste.CENARIOS_S9).isNotEmpty();
        criarCenariosProprios();
        leitura = SigaOracleTeste.dataSourceLeitura();
        lancamentos = new LancamentosDoSigaJdbc(new NamedParameterJdbcTemplate(contando(leitura)), 7, 200);
    }

    @AfterAll
    static void fechar() {
        leitura.close();
    }

    @BeforeEach
    void zerar() {
        SQL_ENVIADO.clear();
        TEMPOS_LIMITE.clear();
    }

    private static void criarCenariosProprios() {
        try (HikariDataSource admin = SigaOracleTeste.dataSourceAdministrador()) {
            NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(admin);
            Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = :id",
                    new MapSqlParameterSource("id", STATUS_DESCONHECIDO), Integer.class);
            if (existe != null && existe > 0) {
                return;
            }
            CamundaFicticioBuilder construtor = new CamundaFicticioBuilder(jdbc, CatalogoDeEtapasDeTeste.padrao());
            construtor.processo(Fluxo.HIDRICO, STATUS_DESCONHECIDO).situacao("EM_ANALISE").semInstancia()
                    .abertoEm(LocalDateTime.of(2026, 3, 2, 8, 0)).desde(LocalDateTime.of(2026, 3, 10, 8, 0))
                    .comLancamentoBruto("ESTORNADA_FICTICIA").comDar("FICT-9201")
                    .emitidoEm(LocalDateTime.of(2026, 3, 4, 9, 0)).gravar();
            construtor.processo(Fluxo.HIDRICO, PAGA_SEM_DADOS).situacao("EM_ANALISE").semInstancia()
                    .abertoEm(LocalDateTime.of(2026, 3, 2, 8, 0)).desde(LocalDateTime.of(2026, 3, 10, 8, 0))
                    .comLancamentoBruto("PAGA").emitidoEm(LocalDateTime.of(2026, 3, 4, 9, 0)).gravar();
        }
    }

    private static LancamentosClassificados classificado(long id) {
        return ClassificarValidade.classificar(lancamentos.lerDoRequerimento(id));
    }

    private static List<String> status(LancamentosClassificados c) {
        return c.mantidos().stream().map(m -> m.lancamento().statusBruto()).toList();
    }

    @Test
    void fin01_paga_com_todos_os_dados() {
        LancamentosClassificados c = classificado(FIN01);
        assertThat(c.mantidos()).singleElement().satisfies(m -> {
            Lancamento l = m.lancamento();
            assertThat(m.consistente()).isTrue();
            assertThat(l.status()).contains(StatusDaGuia.PAGA);
            assertThat(l.numeroDar()).isEqualTo("FICT-0001");
            assertThat(l.idRequerimento()).isEqualTo(FIN01);
            assertThat(l.emitidoEm()).isEqualTo(CamundaFicticioBuilder.dias(2));
            assertThat(l.pagoEm()).isEqualTo(CamundaFicticioBuilder.dias(5));
            assertThat(l.venceEm()).isEqualTo(CamundaFicticioBuilder.dias(12).toLocalDate().atStartOfDay());
            assertThat(l.ativo()).isTrue();
            assertThat(l.valor()).isEqualByComparingTo(new BigDecimal("1250.50"));
            assertThat(l.desconto()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(l.upf()).isEqualByComparingTo(new BigDecimal("35.7286"));
            assertThat(l.taxaIsenta()).isFalse();
        });
        assertThat(c.ignorados()).isEmpty();
    }

    @Test
    void fin05_e_fin06_isentas_sem_dar_validas_com_o_processado_como_esta() {
        LancamentosClassificados p = classificado(FIN05);
        LancamentosClassificados n = classificado(FIN06);
        assertThat(p.mantidos()).singleElement().satisfies(m -> {
            assertThat(m.consistente()).isTrue();
            assertThat(m.lancamento().temDar()).isFalse();
            assertThat(m.lancamento().taxaIsenta()).isTrue();
            assertThat(m.lancamento().statusProcessado()).isEqualTo("P");
            assertThat(m.lancamento().valor()).isNull();
        });
        assertThat(n.mantidos()).singleElement().satisfies(m -> {
            assertThat(m.consistente()).isTrue();
            assertThat(m.lancamento().statusProcessado()).isEqualTo("N");
        });
    }

    @Test
    void fin07_cancelada_sem_dar_ignorada_ao_lado_da_valida() {
        LancamentosClassificados c = classificado(FIN07);
        assertThat(status(c)).containsExactly("AGUARDANDO_PAGAMENTO");
        assertThat(c.ignoradosPorMotivo()).containsEntry(MotivoDoIgnorado.FALSO_SEM_DAR, 1);
    }

    @Test
    void fin08_cancelada_com_dar_e_encerrada_e_valida() {
        LancamentosClassificados c = classificado(FIN08);
        assertThat(c.mantidos()).singleElement().satisfies(m -> {
            assertThat(m.consistente()).isTrue();
            assertThat(m.lancamento().status()).contains(StatusDaGuia.CANCELADA);
            assertThat(m.lancamento().ativo()).isFalse();
        });
    }

    @Test
    void fin09_aguardando_emissao_sem_dar_e_valido() {
        assertThat(classificado(FIN09).mantidos()).singleElement()
                .satisfies(m -> assertThat(m.consistente()).isTrue());
    }

    @Test
    void fin10_ruido_isento_aguardando_e_ignorado() {
        LancamentosClassificados c = classificado(FIN10);
        assertThat(c.semLancamento()).isTrue();
        assertThat(c.ignoradosPorMotivo()).containsEntry(MotivoDoIgnorado.RUIDO_ISENTO_AGUARDANDO, 1)
                .containsEntry(MotivoDoIgnorado.FALSO_SEM_DAR, 0);
    }

    @Test
    void fin13_sem_lancamento() {
        assertThat(lancamentos.lerDoRequerimento(FIN13)).isEqualTo(LeituraDosLancamentos.NENHUM);
        assertThat(SQL_ENVIADO).hasSize(1);
    }

    @Test
    void fin14_dez_falsos_e_um_valido() {
        LancamentosClassificados c = classificado(FIN14);
        assertThat(c.mantidos()).singleElement()
                .satisfies(m -> assertThat(m.lancamento().numeroDar()).isEqualTo("FICT-0014"));
        assertThat(c.ignoradosPorMotivo()).containsEntry(MotivoDoIgnorado.FALSO_SEM_DAR, FALSOS_DO_FIN14);
        assertThat(c.avisos()).isEmpty();
    }

    @Test
    void status_desconhecido_e_mantido_com_aviso() {
        LancamentosClassificados c = classificado(STATUS_DESCONHECIDO);
        assertThat(c.mantidos()).singleElement().satisfies(m -> {
            assertThat(m.lancamento().statusBruto()).isEqualTo("ESTORNADA_FICTICIA");
            assertThat(m.inconsistencias()).containsExactly(Inconsistencia.STATUS_DESCONHECIDO);
        });
        assertThat(c.avisos()).extracting(a -> a.codigo()).containsExactly(CodigoDoAvisoFinanceiro.LANCAMENTO_INCONSISTENTE);
    }

    @Test
    void paga_sem_dar_e_sem_pagamento_e_mantida_com_aviso() {
        assertThat(classificado(PAGA_SEM_DADOS).mantidos()).singleElement()
                .extracting(LancamentoMantido::inconsistencias).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.collection(Inconsistencia.class))
                .containsExactlyInAnyOrder(Inconsistencia.SEM_DAR, Inconsistencia.PAGA_SEM_DATA_DE_PAGAMENTO);
    }

    // ---- consultas ----

    @Test
    void uma_consulta_por_requerimento_com_tempo_limite() {
        lancamentos.lerDoRequerimento(FIN14);
        assertThat(SQL_ENVIADO).hasSize(1);
        assertThat(TEMPOS_LIMITE).containsExactly(7);
    }

    @Test
    void uma_consulta_por_lote_com_o_mesmo_resultado_da_leitura_individual() {
        List<Long> ids = List.of(FIN01, FIN05, FIN07, FIN10, FIN13, FIN14, FIN07);
        Map<Long, LeituraDosLancamentos> lote = lancamentos.lerEmLote(ids);
        assertThat(SQL_ENVIADO).hasSize(1);
        assertThat(lote).containsOnlyKeys(FIN01, FIN05, FIN07, FIN10, FIN13, FIN14);
        assertThat(lote.get(FIN13)).isEqualTo(LeituraDosLancamentos.NENHUM);
        for (long id : lote.keySet()) {
            assertThat(lote.get(id)).as("requerimento %d", id).isEqualTo(lancamentos.lerDoRequerimento(id));
        }
    }

    @Test
    void lote_acima_de_500_e_particionado() {
        List<Long> ids = LongStream.rangeClosed(1, 501).map(i -> 9_500_000 + i).boxed().toList();
        Map<Long, LeituraDosLancamentos> lote = lancamentos.lerEmLote(ids);
        assertThat(SQL_ENVIADO).hasSize(2);
        assertThat(TEMPOS_LIMITE).containsOnly(7);
        assertThat(lote).hasSize(501).allSatisfy((id, l) -> assertThat(l).isEqualTo(LeituraDosLancamentos.NENHUM));
    }

    @Test
    void lote_vazio_nao_consulta() {
        assertThat(lancamentos.lerEmLote(List.of())).isEmpty();
        assertThat(SQL_ENVIADO).isEmpty();
    }

    @Test
    void limite_por_requerimento_marca_alem_do_limite_sem_afetar_os_outros() {
        LancamentosDoSigaJdbc curto = new LancamentosDoSigaJdbc(new NamedParameterJdbcTemplate(contando(leitura)), 7, 5);
        Map<Long, LeituraDosLancamentos> lote = curto.lerEmLote(List.of(FIN14, FIN07));
        assertThat(SQL_ENVIADO).hasSize(1);
        assertThat(lote.get(FIN14).lancamentos()).hasSize(5);
        assertThat(lote.get(FIN14).alemDoLimite()).isTrue();
        assertThat(lote.get(FIN07).lancamentos()).hasSize(2);
        assertThat(lote.get(FIN07).alemDoLimite()).isFalse();
        assertThat(ClassificarValidade.classificar(lote.get(FIN14)).avisos()).extracting(a -> a.codigo())
                .containsExactly(CodigoDoAvisoFinanceiro.LANCAMENTOS_ALEM_DO_LIMITE);
    }

    @Test
    void sql_enviado_so_le_as_colunas_permitidas() {
        lancamentos.lerDoRequerimento(FIN01);
        lancamentos.lerEmLote(List.of(FIN01, FIN05));
        assertThat(SQL_ENVIADO).hasSize(2).allSatisfy(sql -> {
            String maiusculo = sql.toUpperCase(java.util.Locale.ROOT);
            assertThat(maiusculo).doesNotContain("CHAVE_GED").doesNotContain("MENSAGEM").doesNotContain("ID_TITULACAO");
            assertThat(maiusculo).doesNotContainPattern("SELECT\\s+\\*").doesNotContainPattern("\\.\\s*\\*");
            assertThat(maiusculo.strip()).doesNotContainPattern("\\b(INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP)\\b");
            assertThat(maiusculo).contains("FETCH  FIRST ?").contains("IN (?");
        });
    }

    @Test
    void banco_fora_do_ar_lanca_para_o_caso_de_uso_tratar() {
        try (HikariDataSource fora = SigaOracleTeste.dataSourceInacessivel()) {
            LancamentosDoSigaJdbc indisponivel = new LancamentosDoSigaJdbc(new NamedParameterJdbcTemplate(fora), 1, 200);
            assertThatThrownBy(() -> indisponivel.lerDoRequerimento(FIN01)).isInstanceOf(DataAccessException.class);
        }
    }

    /** Guarda cada SQL preparado (uma consulta) e cada tempo limite aplicado a ele. */
    private static DataSource contando(DataSource alvo) {
        ClassLoader cl = LancamentosDoSigaJdbcTest.class.getClassLoader();
        return (DataSource) Proxy.newProxyInstance(cl, new Class<?>[] {DataSource.class}, (proxy, metodo, args) -> {
            Object resultado = invocar(alvo, metodo, args);
            if (resultado instanceof Connection conexao) {
                return Proxy.newProxyInstance(cl, new Class<?>[] {Connection.class}, (p2, m2, a2) -> {
                    if (m2.getName().startsWith("prepare") || m2.getName().equals("createStatement")) {
                        SQL_ENVIADO.add(a2 != null && a2.length > 0 ? String.valueOf(a2[0]) : "");
                    }
                    Object criado = invocar(conexao, m2, a2);
                    if (criado instanceof PreparedStatement comando) {
                        return Proxy.newProxyInstance(cl, new Class<?>[] {PreparedStatement.class}, (p3, m3, a3) -> {
                            if (m3.getName().equals("setQueryTimeout")) {
                                TEMPOS_LIMITE.add((Integer) a3[0]);
                            }
                            return invocar(comando, m3, a3);
                        });
                    }
                    return criado;
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
