package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.Marco;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.TipoDeMarco;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoPlanoB;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.MontarHistoricoAproximado;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.MapaDeEventosDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Plano B (S8, bloco 5) sobre as fixtures PB01 a PB03, pelo usuário de leitura: mapeamento, ordem, evento desconhecido,
 * só situação e datas, duas consultas e a coluna {@code CPF_USUARIO} (que as fixtures preenchem) nunca lida.
 */
@Testcontainers(disabledWithoutDocker = true)
class PlanoBComFixturesTest {

    private static final ModeloDeFluxo HIDRICO = CatalogoDeEtapasDeTeste.padrao().modelo("procOutorgaAguaSupSub")
            .orElseThrow();
    private static final LocalDateTime A2021 = LocalDateTime.of(2021, 5, 10, 9, 0);
    private static final Pattern ONZE_DIGITOS = Pattern.compile("(?<![0-9])[0-9]{11}(?![0-9])");
    private static final AtomicInteger CONSULTAS = new AtomicInteger();

    private static HikariDataSource leitura;
    private static CamundaPosicaoJdbc camunda;

    @BeforeAll
    static void abrir() {
        assertThat(SigaOracleTeste.CENARIOS_S8).isNotEmpty();
        leitura = SigaOracleTeste.dataSourceLeitura();
        camunda = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(contando(leitura)), 10);
    }

    @AfterAll
    static void fechar() {
        leitura.close();
    }

    private static HistoricoAproximado aproximado(long id) {
        return MontarHistoricoAproximado.montar(camunda.lerPlanoB(id), HIDRICO, MapaDeEventosDeTeste.padrao());
    }

    private static List<String> ids(HistoricoAproximado h) {
        return h.etapas().stream().map(e -> e.etapa().id()).toList();
    }

    @Test
    void pb01_historico_completo_vira_linha_aproximada_em_ordem() {
        assertThat(camunda.lerCaminho(PB01, HIDRICO.chave()).vazio()).isTrue();
        HistoricoAproximado h = aproximado(PB01);
        assertThat(ids(h)).containsExactly("cadastro_do_requerimento", "conferencia_dos_documentos",
                "aguardando_pagamento", "analise_do_processo", "emissao_do_ato", "registro_do_comprovante_de_publicacao");
        assertThat(h.etapas()).extracting(HistoricoAproximado.EtapaAproximada::entrada).isSorted();
        assertThat(h.etapas().getFirst().entrada()).isEqualTo(A2021);
        assertThat(h.etapas().getLast().saida()).contains(A2021.plusDays(120));
        assertThat(h.marcos()).extracting(Marco::tipo).containsExactly(TipoDeMarco.ABERTURA, TipoDeMarco.PROTOCOLO,
                TipoDeMarco.CONCLUSAO);
        assertThat(h.situacao()).contains("CONCLUIDO");
        assertThat(h.eventosIgnorados()).isZero();
        assertThat(h.aproximado()).isTrue();
    }

    @Test
    void pb02_evento_desconhecido_e_ignorado_e_contado() {
        HistoricoAproximado h = aproximado(PB02);
        assertThat(ids(h)).containsExactly("cadastro_do_requerimento", "conferencia_dos_documentos");
        assertThat(h.eventosIgnorados()).isEqualTo(1);
        assertThat(h.marcos()).extracting(Marco::tipo).contains(TipoDeMarco.EXPIRACAO_DO_REQUERIMENTO);
        assertThat(h.etapas().getLast().saida()).contains(A2021.plusDays(40));
    }

    @Test
    void pb03_sem_eventos_so_situacao_e_datas() {
        HistoricoAproximado h = aproximado(PB03);
        assertThat(h.semDetalhamento()).isTrue();
        assertThat(h.situacao()).contains("EM_ANALISE");
        assertThat(h.marcos()).extracting(Marco::tipo).containsExactly(TipoDeMarco.ABERTURA);
    }

    @Test
    void duas_consultas_e_nenhum_cpf_na_leitura() {
        for (long id : new long[] {PB01, PB02, PB03}) {
            CONSULTAS.set(0);
            LeituraDoPlanoB leituraDoPlanoB = camunda.lerPlanoB(id);
            assertThat(CONSULTAS.get()).isEqualTo(CamundaPosicaoJdbc.CONSULTAS_DO_PLANO_B);
            assertThat(ONZE_DIGITOS.matcher(leituraDoPlanoB.toString()).find()).as("PB " + id).isFalse();
            assertThat(ONZE_DIGITOS.matcher(aproximado(id).toString()).find()).as("PB " + id).isFalse();
        }
    }

    @Test
    void o_sql_do_plano_b_nao_le_cpf_nem_servidor() {
        for (String arquivo : new String[] {"tramitacao/plano-b-eventos", "tramitacao/plano-b-requerimento"}) {
            String sql = SigaSql.carregar(arquivo).toUpperCase(Locale.ROOT);
            String semComentarios = sql.lines().filter(l -> !l.strip().startsWith("--")).reduce("", (a, b) -> a + "\n" + b);
            assertThat(semComentarios).as(arquivo).doesNotContain("CPF").doesNotContain("SERVIDOR").doesNotContain("PESSOA")
                    .doesNotContain("SELECT *");
        }
    }

    private static DataSource contando(DataSource alvo) {
        return (DataSource) Proxy.newProxyInstance(PlanoBComFixturesTest.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, metodo, args) -> {
                    Object resultado = invocar(alvo, metodo, args);
                    if (resultado instanceof Connection conexao) {
                        return Proxy.newProxyInstance(PlanoBComFixturesTest.class.getClassLoader(),
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
