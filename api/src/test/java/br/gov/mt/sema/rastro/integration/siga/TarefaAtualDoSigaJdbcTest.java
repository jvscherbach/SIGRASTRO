package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import br.gov.mt.sema.rastro.consulta.domain.ResponsavelTarefa;
import br.gov.mt.sema.rastro.consulta.domain.TarefaAtual;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class TarefaAtualDoSigaJdbcTest {

    private static HikariDataSource dataSource;
    private static TarefaAtualDoSigaJdbc tarefas;

    @BeforeAll
    static void abrir() {
        dataSource = SigaOracleTeste.dataSourceLeitura();
        tarefas = new TarefaAtualDoSigaJdbc(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
    }

    @Test
    void requerimentoComTarefaAtivaTrazNomeDesdeQuandoResponsavelEFluxo() {
        TarefaAtual tarefa = tarefas.obter(101);

        assertThat(tarefa.estado()).isEqualTo(TarefaAtual.Estado.ATIVA);
        assertThat(tarefa.nome()).isEqualTo("Analisar requerimento");
        assertThat(tarefa.desde()).isEqualTo(LocalDateTime.of(2025, 4, 29, 9, 14));
        // "analista.demo" não é CPF de 11 dígitos: responsável não identificado, e o valor cru não sai.
        assertThat(tarefa.responsavel()).isEqualTo(ResponsavelTarefa.naoIdentificado());
        assertThat(tarefa.fluxo()).isEqualTo("Outorga de Recursos Hídricos");
    }

    @Test
    void instanciaSemTarefaOuSemInstanciaVirouSemTarefa() {
        assertThat(tarefas.obter(102).estado()).isEqualTo(TarefaAtual.Estado.SEM_TAREFA);
        assertThat(tarefas.obter(103).estado()).isEqualTo(TarefaAtual.Estado.SEM_TAREFA);
    }

    @Test
    void falhaDeConexaoNaoLancaVirouIndisponivel() {
        try (HikariDataSource fora = SigaOracleTeste.dataSourceInacessivel()) {
            TarefaAtualDoSigaJdbc indisponivel = new TarefaAtualDoSigaJdbc(new NamedParameterJdbcTemplate(fora));

            assertThat(indisponivel.obter(101).estado()).isEqualTo(TarefaAtual.Estado.INDISPONIVEL);
        }
    }

    @Test
    void faltaDeGrantNoMotorDeFluxoNaoLancaVirouIndisponivel() {
        try (HikariDataSource semGrant = SigaOracleTeste.dataSource(SigaOracleTeste.SEM_PERMISSAO,
                SigaOracleTeste.SENHA_SEM_PERMISSAO)) {
            TarefaAtualDoSigaJdbc sem = new TarefaAtualDoSigaJdbc(new NamedParameterJdbcTemplate(semGrant));

            assertThat(sem.obter(101).estado()).isEqualTo(TarefaAtual.Estado.INDISPONIVEL);
        }
    }

    // --- S1.1: responsável resolvido no SQL; só o nome sai do adapter

    @Test
    void servidorComDuasVersoesUsaONomeMaisRecenteEPrefereServidorAPessoa() {
        assertThat(tarefas.obter(301).responsavel()).isEqualTo(ResponsavelTarefa.atribuido("Maria Nova de Souza"));
        assertThat(tarefas.obter(306).responsavel()).isEqualTo(ResponsavelTarefa.atribuido("Servidor Quatro"));
    }

    @Test
    void semServidorUsaONomeDaPessoa() {
        assertThat(tarefas.obter(302).responsavel()).isEqualTo(ResponsavelTarefa.atribuido("Fulana Só em Pessoa"));
    }

    @Test
    void cpfSemNomeEmNenhumaTabelaEhNaoIdentificado() {
        assertThat(tarefas.obter(303).responsavel()).isEqualTo(ResponsavelTarefa.naoIdentificado());
    }

    @Test
    void assigneeNuloEhNaoAtribuida() {
        TarefaAtual tarefa = tarefas.obter(304);

        assertThat(tarefa.estado()).isEqualTo(TarefaAtual.Estado.ATIVA);
        assertThat(tarefa.responsavel()).isEqualTo(ResponsavelTarefa.naoAtribuida());
    }

    @Test
    void outroFormatoComoDemoEhNaoIdentificado() {
        assertThat(tarefas.obter(305).responsavel()).isEqualTo(ResponsavelTarefa.naoIdentificado());
    }

    @Test
    void oCpfDoResponsavelNaoApareceEmNenhumCampoDoDominio() {
        for (long id = 301; id <= 306; id++) {
            assertThat(tarefas.obter(id).toString()).doesNotContainPattern("[0-9]{11}");
        }
    }
}
