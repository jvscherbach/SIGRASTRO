package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento.Pessoa;
import br.gov.mt.sema.rastro.documento.domain.PapelDaPessoa;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Consulta das pessoas do requerimento para o SCORP (S4) no recorte de teste (05-pessoas-s4.sql), só com SELECT. */
@Testcontainers(disabledWithoutDocker = true)
class PessoasDoSigaJdbcTest {

    private static HikariDataSource dataSource;
    private static PessoasDoSigaJdbc adapter;

    @BeforeAll
    static void abrir() {
        dataSource = SigaOracleTeste.dataSourceLeitura();
        adapter = new PessoasDoSigaJdbc(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
    }

    @Test
    void interessadoRepresentantesETecnicosDistintosPorPapelSemIdCorporativoNulo() {
        assertThat(adapter.listarPessoas(101)).containsExactlyInAnyOrder(
                new Pessoa("CORP-0001", PapelDaPessoa.INTERESSADO),
                new Pessoa("CORP-0001", PapelDaPessoa.REPRESENTANTE_LEGAL),
                new Pessoa("CORP-0050", PapelDaPessoa.REPRESENTANTE_LEGAL),
                new Pessoa("CORP-0050", PapelDaPessoa.RESPONSAVEL_TECNICO));
    }

    @Test
    void requerimentoSemPessoaComIdCorporativoOuInexistenteVemVazio() {
        assertThat(adapter.listarPessoas(102)).isEmpty();
        assertThat(adapter.listarPessoas(105)).isEmpty();
        assertThat(adapter.listarPessoas(-1)).isEmpty();
    }

    @Test
    void conexaoRecusadaViraSigaIndisponivel() {
        try (HikariDataSource fora = SigaOracleTeste.dataSourceInacessivel()) {
            PessoasDoSigaJdbc indisponivel = new PessoasDoSigaJdbc(new NamedParameterJdbcTemplate(fora));

            assertThatThrownBy(() -> indisponivel.listarPessoas(101)).isInstanceOf(SigaIndisponivelException.class);
        }
    }
}
