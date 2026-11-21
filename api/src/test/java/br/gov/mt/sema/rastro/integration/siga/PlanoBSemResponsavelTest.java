package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import br.gov.mt.sema.rastro.tramitacao.api.dto.TramitacaoResponse;
import br.gov.mt.sema.rastro.tramitacao.application.MontarTramitacao;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento.Estado;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDoConfrontoDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.MapaDeEventosDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S8, bloco 11: o plano B <b>nunca</b> consulta {@code SERVIDOR} (nem {@code PESSOA}, nem coluna de CPF) e <b>nunca</b>
 * devolve responsável. Prova pelo SQL de fato emitido ao Oracle de teste (capturado no {@code prepareStatement}) na
 * montagem real da tramitação dos cenários PB01 a PB03, cujo {@code HISTORICO_REQUERIMENTO} tem {@code CPF_USUARIO}
 * fictício preenchido de propósito; e pelo contrato HTTP do plano B, que não tem campo de responsável.
 */
@Testcontainers(disabledWithoutDocker = true)
class PlanoBSemResponsavelTest {

    private static final List<String> SQL_EMITIDO = new CopyOnWriteArrayList<>();
    private static HikariDataSource leitura;
    private static MontarTramitacao montar;

    @BeforeAll
    static void abrir() {
        assertThat(SigaOracleTeste.CENARIOS_S8).isNotEmpty();
        leitura = SigaOracleTeste.dataSourceLeitura();
        CamundaPosicaoJdbc camunda = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(capturando(leitura)), 10);
        montar = new MontarTramitacao(camunda, camunda, camunda, camunda, CatalogoDeEtapasDeTeste.padrao(),
                MapaDeEventosDeTeste.padrao(), CatalogoDoConfrontoDeTeste.padrao());
    }

    @AfterAll
    static void fechar() {
        leitura.close();
    }

    @BeforeEach
    void limpar() {
        SQL_EMITIDO.clear();
    }

    /** O SQL sem os comentários (que podem citar o que não é lido, como "CPF_USUARIO nunca é lida"). */
    private static String semComentarios(String sql) {
        return sql.lines().map(l -> l.replaceAll("--.*$", "")).collect(Collectors.joining("\n"))
                .toUpperCase(Locale.ROOT);
    }

    @Test
    void plano_b_nao_emite_sql_com_servidor_pessoa_nem_cpf_e_nao_devolve_responsavel() {
        for (long id : new long[] {PB01, PB02, PB03}) {
            SQL_EMITIDO.clear();
            TramitacaoDoRequerimento t = montar.montar(id);
            assertThat(t.estado()).as("PB " + id).isEqualTo(Estado.SEM_INSTANCIA);
            assertThat(t.planoB()).as("PB " + id).isPresent();
            assertThat(t.caminho().passagens()).as("PB " + id).isEmpty();
            assertThat(SQL_EMITIDO).as("PB " + id).isNotEmpty().allSatisfy(sql -> assertThat(semComentarios(sql))
                    .doesNotContain("SERVIDOR").doesNotContain("PESSOA").doesNotContain("CPF")
                    .doesNotContain("ASSIGNEE_"));
            assertThat(t.toString()).as("PB " + id).doesNotContainPattern("(?<![0-9])\\d{11}(?![0-9])")
                    .doesNotContain("Servidor Exemplo");
        }
    }

    @Test
    void a_captura_funciona_o_caminho_do_camunda_consulta_servidor_para_o_nome() {
        TramitacaoDoRequerimento t = montar.montar(HC01);
        assertThat(t.estado()).isEqualTo(Estado.ATIVO);
        assertThat(SQL_EMITIDO).anySatisfy(sql -> assertThat(semComentarios(sql)).contains("SERVIDOR"));
    }

    @Test
    void contrato_do_plano_b_nao_tem_campo_de_responsavel_nome_nem_cpf() {
        Set<String> campos = new HashSet<>();
        componentes(TramitacaoResponse.HistoricoAproximadoResponse.class, campos, new HashSet<>());
        // Só o nome do campo (depois do ponto): o nome da classe, "...Response", não conta.
        assertThat(campos).isNotEmpty().noneMatch(c -> c.substring(c.indexOf('.') + 1).toLowerCase(Locale.ROOT)
                .matches(".*(respons|particip|nome|cpf|assignee).*"));
    }

    /** Nomes de todos os componentes do record e dos records que ele contém (listas incluídas). */
    private static void componentes(Class<?> tipo, Set<String> nomes, Set<Class<?>> vistos) {
        if (!tipo.isRecord() || !vistos.add(tipo)) {
            return;
        }
        for (RecordComponent c : tipo.getRecordComponents()) {
            nomes.add(tipo.getSimpleName() + "." + c.getName());
            componentes(c.getType(), nomes, vistos);
            Type generico = c.getGenericType();
            if (generico instanceof ParameterizedType p) {
                for (Type argumento : p.getActualTypeArguments()) {
                    if (argumento instanceof Class<?> classe) {
                        componentes(classe, nomes, vistos);
                    }
                }
            }
        }
    }

    private static DataSource capturando(DataSource alvo) {
        return (DataSource) Proxy.newProxyInstance(PlanoBSemResponsavelTest.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, metodo, args) -> {
                    Object resultado = invocar(alvo, metodo, args);
                    if (resultado instanceof Connection conexao) {
                        return Proxy.newProxyInstance(PlanoBSemResponsavelTest.class.getClassLoader(),
                                new Class<?>[] {Connection.class}, (p2, m2, a2) -> {
                                    if (m2.getName().startsWith("prepare") && a2 != null && a2[0] instanceof String sql) {
                                        SQL_EMITIDO.add(sql);
                                    }
                                    return invocar(conexao, m2, a2);
                                });
                    }
                    return resultado;
                });
    }

    private static Object invocar(Object alvo, Method metodo, Object[] args) throws Throwable {
        try {
            return metodo.invoke(alvo, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

}
