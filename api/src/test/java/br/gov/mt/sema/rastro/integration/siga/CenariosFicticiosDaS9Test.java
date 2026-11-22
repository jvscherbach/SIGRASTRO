package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS9.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDate;
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
 * O construtor grava exatamente o que cada cenário financeiro da S9 descreve (FIN01 a FIN16 e os de apoio). Lê pelo usuário
 * de leitura (prova também o grant de {@code LANCAMENTO_TAXA}). As consultas daqui conferem as fixtures: não são o SQL de
 * produção (bloco 2).
 */
@Testcontainers(disabledWithoutDocker = true)
class CenariosFicticiosDaS9Test {

    private static HikariDataSource dataSource;
    private static NamedParameterJdbcTemplate jdbc;
    private static Map<String, Resultado> cenarios;

    @BeforeAll
    static void abrir() {
        cenarios = SigaOracleTeste.CENARIOS_S9;
        dataSource = SigaOracleTeste.dataSourceLeitura();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
    }

    /** Linha de LANCAMENTO_TAXA resumida: status, DAR, isenção, processado e as datas como data pura. */
    private record Linha(String status, String dar, int isenta, String processado, LocalDate emissao, LocalDate vencimento,
            LocalDate pagamento, LocalDate fim) {
    }

    private static List<Linha> linhas(long requerimento) {
        return jdbc.query("""
                SELECT STATUS, NUM_DAR, TAXA_ISENTA, STATUS_PROCESSADO, DATA_EMISSAO, DATA_VENCIMENTO, DATA_PAGAMENTO, DATA_FIM
                FROM   SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA
                WHERE  ID_REQUERIMENTO = :id
                ORDER BY ID""", new MapSqlParameterSource("id", requerimento), (rs, n) -> new Linha(
                rs.getString("STATUS"), rs.getString("NUM_DAR"), rs.getInt("TAXA_ISENTA"), rs.getString("STATUS_PROCESSADO"),
                data(rs.getTimestamp("DATA_EMISSAO")), data(rs.getTimestamp("DATA_VENCIMENTO")),
                data(rs.getTimestamp("DATA_PAGAMENTO")), data(rs.getTimestamp("DATA_FIM"))));
    }

    private static LocalDate data(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().toLocalDate();
    }

    private static LocalDate dia(int n) {
        return CamundaFicticioBuilder.dias(n).toLocalDate();
    }

    @Test
    void todosOsCenariosForamGravados() {
        assertThat(cenarios).containsKeys("FIN01", "FIN02", "FIN03", "FIN04", "FIN05", "FIN06", "FIN07", "FIN08", "FIN09",
                "FIN10", "FIN11", "FIN12", "FIN13", "FIN14", "FIN15", "FIN16", "VENCE_ONTEM", "VENCE_HOJE", "VENCE_AMANHA");
        assertThat(cenarios.get("FIN15").encerrada()).isTrue();
        assertThat(cenarios.get("FIN16").temInstancia()).isFalse();
        assertThat(cenarios.get("FIN01").temInstancia()).isTrue();
    }

    @Test
    void fin01TaxaPaga() {
        assertThat(linhas(FIN01)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("PAGA");
            assertThat(l.dar()).isEqualTo("FICT-0001");
            assertThat(l.emissao()).isEqualTo(dia(2));
            assertThat(l.vencimento()).isEqualTo(dia(12));
            assertThat(l.pagamento()).isEqualTo(dia(5));
            assertThat(l.fim()).isNull();
        });
    }

    @Test
    void fin02AguardandoNoPrazoEFin03VencidaPorDataComStatusAindaAguardando() {
        assertThat(linhas(FIN02)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("AGUARDANDO_PAGAMENTO");
            assertThat(l.vencimento()).isEqualTo(AMANHA);
            assertThat(l.pagamento()).isNull();
        });
        assertThat(linhas(FIN03)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("AGUARDANDO_PAGAMENTO");
            assertThat(l.vencimento()).isEqualTo(ONTEM);
            assertThat(l.pagamento()).isNull();
            assertThat(l.fim()).isNull();
        });
    }

    @Test
    void fin04MarcadaVencidaPeloSiga() {
        assertThat(linhas(FIN04)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("VENCIDA");
            assertThat(l.dar()).isEqualTo("FICT-0004");
            assertThat(l.pagamento()).isNull();
        });
    }

    @Test
    void fin05EFin06IsentosSemDarComProcessadoPeN() {
        assertThat(linhas(FIN05)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("ISENTA");
            assertThat(l.dar()).isNull();
            assertThat(l.isenta()).isEqualTo(1);
            assertThat(l.processado()).isEqualTo("P");
            assertThat(l.emissao()).isEqualTo(dia(2));
        });
        assertThat(linhas(FIN06)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("ISENTA");
            assertThat(l.dar()).isNull();
            assertThat(l.processado()).isEqualTo("N");
        });
    }

    @Test
    void fin07CanceladaSemDarAoLadoDeUmValido() {
        List<Linha> linhas = linhas(FIN07);
        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).status()).isEqualTo("CANCELADA");
        assertThat(linhas.get(0).dar()).isNull();
        assertThat(linhas.get(0).fim()).isNotNull();
        assertThat(linhas.get(1).status()).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(linhas.get(1).dar()).isEqualTo("FICT-0007");
        assertThat(linhas.get(1).fim()).isNull();
    }

    @Test
    void fin08CanceladaComDarEDataFim() {
        assertThat(linhas(FIN08)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("CANCELADA");
            assertThat(l.dar()).isEqualTo("FICT-0008");
            assertThat(l.fim()).isEqualTo(dia(6));
        });
    }

    @Test
    void fin09AguardandoEmissaoSemDarEFin10RuidoIsentoAguardando() {
        assertThat(linhas(FIN09)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("AGUARDANDO_EMISSAO");
            assertThat(l.dar()).isNull();
            assertThat(l.isenta()).isZero();
        });
        assertThat(linhas(FIN10)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("AGUARDANDO_EMISSAO");
            assertThat(l.dar()).isNull();
            assertThat(l.isenta()).isEqualTo(1);
        });
    }

    @Test
    void fin11TaxaInicialPagaETaxaDaCorrecaoAguardando() {
        List<Linha> linhas = linhas(FIN11);
        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).status()).isEqualTo("PAGA");
        assertThat(linhas.get(0).emissao()).isEqualTo(dia(2));
        assertThat(linhas.get(1).status()).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(linhas.get(1).emissao()).isEqualTo(dia(14));
        // a segunda taxa foi emitida depois da entrada no ramo de correção (dia 12 na tramitação)
        assertThat(linhas.get(1).emissao()).isAfter(dia(12));
    }

    @Test
    void fin12PagoQueVirouIsentoComOsDoisAVista() {
        List<Linha> linhas = linhas(FIN12);
        assertThat(linhas).extracting(Linha::status).containsExactly("PAGA", "ISENTA");
        assertThat(linhas.get(0).dar()).isEqualTo("FICT-0012");
        assertThat(linhas.get(1).dar()).isNull();
        assertThat(linhas.get(1).isenta()).isEqualTo(1);
    }

    @Test
    void fin13SemLancamento() {
        assertThat(linhas(FIN13)).isEmpty();
        Integer requerimentos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = :id",
                new MapSqlParameterSource("id", FIN13), Integer.class);
        assertThat(requerimentos).isEqualTo(1);
    }

    @Test
    void fin14DezCanceladosSemDarEUmValido() {
        List<Linha> linhas = linhas(FIN14);
        assertThat(linhas).hasSize(FALSOS_DO_FIN14 + 1);
        assertThat(linhas.stream().filter(l -> "CANCELADA".equals(l.status()) && l.dar() == null)).hasSize(FALSOS_DO_FIN14);
        assertThat(linhas.stream().filter(l -> l.dar() != null)).singleElement()
                .satisfies(l -> assertThat(l.status()).isEqualTo("AGUARDANDO_PAGAMENTO"));
    }

    @Test
    void fin15EOH18ComTaxaVencidaPorData() {
        Resultado resultado = cenarios.get("FIN15");
        assertThat(resultado.encerrada()).isTrue();
        String final_ = jdbc.queryForObject(
                "SELECT END_ACT_ID_ FROM SIGA2_CAMUNDA.ACT_HI_PROCINST WHERE ID_ = :pi",
                new MapSqlParameterSource("pi", resultado.instancia()), String.class);
        assertThat(final_).isEqualTo("Event_0h1oige");
        String situacao = jdbc.queryForObject("SELECT SITUACAO_PROCESSO FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = :id",
                new MapSqlParameterSource("id", FIN15), String.class);
        assertThat(situacao).isEqualTo("AGUARDANDO_PAGAMENTO_DE_TAXAS");
        assertThat(linhas(FIN15)).singleElement().satisfies(l -> {
            assertThat(l.status()).isEqualTo("AGUARDANDO_PAGAMENTO");
            assertThat(l.vencimento()).isEqualTo(ONTEM);
        });
    }

    @Test
    void fin16SemInstanciaComTaxaPaga() {
        assertThat(cenarios.get("FIN16").temInstancia()).isFalse();
        Integer instancias = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SIGA2_CAMUNDA.ACT_HI_PROCINST WHERE BUSINESS_KEY_ = :id",
                new MapSqlParameterSource("id", Long.toString(FIN16)), Integer.class);
        assertThat(instancias).isZero();
        assertThat(linhas(FIN16)).singleElement().satisfies(l -> assertThat(l.status()).isEqualTo("PAGA"));
    }

    @Test
    void vencimentosEmTornoDeHoje() {
        assertThat(linhas(VENCE_ONTEM)).singleElement().satisfies(l -> assertThat(l.vencimento()).isEqualTo(ONTEM));
        assertThat(linhas(VENCE_HOJE)).singleElement().satisfies(l -> assertThat(l.vencimento()).isEqualTo(HOJE));
        assertThat(linhas(VENCE_AMANHA)).singleElement().satisfies(l -> assertThat(l.vencimento()).isEqualTo(AMANHA));
    }

    @Test
    void asColunasQueOFinanceiroNuncaLeFicamNulasEOsDarsSaoFicticios() {
        Integer comColunaProibida = jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA
                WHERE  CHAVE_GED IS NOT NULL OR MENSAGEM IS NOT NULL OR ID_TITULACAO IS NOT NULL""",
                new MapSqlParameterSource(), Integer.class);
        assertThat(comColunaProibida).isZero();
        Integer darReal = jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA
                WHERE  NUM_DAR IS NOT NULL AND NUM_DAR NOT LIKE 'FICT-%'""", new MapSqlParameterSource(), Integer.class);
        assertThat(darReal).isZero();
    }

    @Test
    void ousuarioDeLeituraSoLeELancamentos() {
        // O usuário de leitura tem apenas SELECT: uma escrita deve falhar.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA WHERE ID = :id", new MapSqlParameterSource("id", 1L)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
