package br.gov.mt.sema.vistas.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import br.gov.mt.sema.vistas.consulta.domain.CriterioBusca;
import br.gov.mt.sema.vistas.consulta.domain.NumeroRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.TarefaAtual;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.OrigemDocumento;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Teste de fumaça contra o SIGA REAL, somente leitura, para o responsável executar na homologação
 * (docs/homologacao/S1-roteiro-homologacao.md). Nunca roda no verify normal: só com SIGA_SMOKE=true.
 *
 * Executa cada consulta do integration.siga com valores que não casam com nenhum registro. O que se prova é que a
 * conexão abre, que o usuário tem grant em todas as tabelas e que todas as colunas usadas existem e são legíveis.
 * Não grava nada e não lê dados de pessoas.
 *
 * <pre>
 * SIGA_SMOKE=true SIGA_DB_URL=... SIGA_DB_USER=... SIGA_DB_PASSWORD=... ./mvnw -Dtest=SigaFumacaTest test
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "SIGA_SMOKE", matches = "true")
class SigaFumacaTest {

    private static HikariDataSource dataSource;
    private static NamedParameterJdbcTemplate jdbc;

    @BeforeAll
    static void conectar() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(System.getenv("SIGA_DB_URL"));
        config.setUsername(System.getenv("SIGA_DB_USER"));
        config.setPassword(System.getenv("SIGA_DB_PASSWORD"));
        config.setReadOnly(true);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.addDataSourceProperty("oracle.jdbc.ReadTimeout", "30000");
        dataSource = new HikariDataSource(config);
        jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @AfterAll
    static void desconectar() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void conectaEOUsuarioDeLeituraLeTodasAsTabelasEColunasUsadas() {
        RequerimentosDoSigaJdbc requerimentos = new RequerimentosDoSigaJdbc(jdbc);
        NumeroRequerimento inexistente = NumeroRequerimento.de("999999/1900");

        assertThat(requerimentos.buscarPorNumeroDeRequerimento(inexistente)).isEmpty();
        assertThat(requerimentos.buscarPorId(-1)).isEmpty();
        // Lista e contagem com todos os predicados possíveis ligados.
        CriterioBusca todos = CriterioBusca.de("REQUERIMENTO", "999999/1900", "00000000000", "zzzzzz", 0, 1)
                .comRestricao("00000000000");
        assertThat(requerimentos.buscarPorCriterios(todos).total()).isZero();
        CriterioBusca porProcesso = CriterioBusca.de("PROCESSO", "999999/1900", null, null, 0, 1);
        assertThat(requerimentos.buscarPorCriterios(porProcesso).total()).isZero();

        ObjetoEPrioridadeDoSigaJdbc objeto = new ObjetoEPrioridadeDoSigaJdbc(jdbc);
        assertThat(objeto.objeto(-1).objetivos()).isEmpty();
        assertThat(objeto.prioridade(-1).criterios()).isEmpty();
    }

    @Test
    void leTarefaAtualSemFalharNoMotorDeFluxo() {
        TarefaAtualDoSigaJdbc tarefas = new TarefaAtualDoSigaJdbc(jdbc);

        // Falha de leitura viraria INDISPONIVEL (o adapter não lança); para um id inexistente o esperado é SEM_TAREFA.
        assertThat(tarefas.obter(-1).estado()).isEqualTo(TarefaAtual.Estado.SEM_TAREFA);
    }

    @Test
    void cadaOrigemDeDocumentoExecutaORealSqlComRequerimentoInexistente() {
        List<String> falhas = new ArrayList<>();
        for (OrigemDocumento origem : OrigemDocumento.values()) {
            if (!origem.doSiga()) {
                continue;
            }
            try {
                // -1 valida tabela, coluna e junção sem depender de dado; falha vira exceção.
                new DocumentosDoSigaJdbc(jdbc, EnumSet.of(origem)).listar(-1);
            } catch (RuntimeException e) {
                falhas.add(origem.name() + " (" + e.getClass().getSimpleName() + ")");
            }
        }

        assertThat(falhas).as("origens cujo SQL falhou no SIGA real (consulte o roteiro de homologação)").isEmpty();
    }

    /**
     * Só com SIGA_FUMACA_REQUERIMENTO (id numérico definido pelo responsável). Imprime apenas a contagem por origem e,
     * por documento, origem, id, situação, tipo e sigilo: nunca chave, título nem nome do arquivo.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "SIGA_FUMACA_REQUERIMENTO", matches = "[0-9]+")
    void listaContagemPorOrigemDeUmRequerimentoReal() {
        long id = Long.parseLong(System.getenv("SIGA_FUMACA_REQUERIMENTO"));
        List<Documento> lista = new DocumentosDoSigaJdbc(jdbc, EnumSet.allOf(OrigemDocumento.class)).listar(id);

        for (OrigemDocumento origem : OrigemDocumento.values()) {
            if (!origem.doSiga()) {
                continue;
            }
            System.out.println("[fumaca] " + origem + ": " + lista.stream().filter(d -> d.origem() == origem).count());
        }
        for (Documento d : lista) {
            System.out.println("[fumaca] " + d.origem() + " id=" + d.id() + " situacao=" + d.situacao() + " tipo="
                    + d.tipoArquivo() + " sigiloso=" + d.sigiloso());
        }
        assertThat(lista).isNotNull();
    }
}
