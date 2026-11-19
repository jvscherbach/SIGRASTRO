package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.mt.sema.rastro.consulta.domain.CriterioPrioridade;
import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento.AtividadeLicenciamento;
import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento.FinalidadeAtividade;
import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento.TipoObjetivo;
import br.gov.mt.sema.rastro.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ObjetoEPrioridadeDoSigaJdbcTest {

    private static HikariDataSource dataSource;
    private static ObjetoEPrioridadeDoSigaJdbc siga;

    @BeforeAll
    static void abrir() {
        dataSource = SigaOracleTeste.dataSourceLeitura();
        siga = new ObjetoEPrioridadeDoSigaJdbc(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
    }

    // --- objeto de outorga

    @Test
    void outorgaTrazObjetivosVigentesEParesSemMultiplicarLinhas() {
        ObjetoDoRequerimento objeto = siga.objeto(101);

        // 4 ligações: a vencida some, a de DATA_FIM futura aparece. Ordem pelo id da ligação.
        assertThat(objeto.objetivos()).containsExactly("Consumo humano", "Irrigação", "Barragem de regularização");
        assertThat(objeto.finalidadesAtividades()).containsExactly(
                new FinalidadeAtividade("Abastecimento público", "Captação para abastecimento"),
                new FinalidadeAtividade("Agricultura", null));
        assertThat(objeto.tiposObjetivo()).containsExactly(
                new TipoObjetivo("SUBTERRANEA", "Subterrânea", "Consumo humano"),
                new TipoObjetivo("SUPERFICIAL", "Superficial", "Irrigação"),
                new TipoObjetivo("BARRAMENTO", "Barramento", "Barragem de regularização"));
        assertThat(objeto.atividadeLicenciamento()).isNull();
    }

    @Test
    void objetivoComDataFimVencidaNaoAparece() {
        assertThat(siga.objeto(101).objetivos()).doesNotContain("Objetivo vencido");
    }

    // --- objeto de licenciamento

    @Test
    void licenciamentoComObjetivosTrazAtividadeGrupoEObjetivos() {
        ObjetoDoRequerimento objeto = siga.objeto(102);

        assertThat(objeto.objetivos()).containsExactly("Licença prévia", "Licença de instalação");
        assertThat(objeto.atividadeLicenciamento())
                .isEqualTo(new AtividadeLicenciamento("Mineração", "Extração de areia"));
        assertThat(objeto.finalidadesAtividades()).isEmpty();
        assertThat(objeto.tiposObjetivo()).isEmpty();
    }

    @Test
    void licenciamentoSemObjetivosTrazSoAAtividade() {
        ObjetoDoRequerimento objeto = siga.objeto(103);

        assertThat(objeto.objetivos()).isEmpty();
        assertThat(objeto.atividadeLicenciamento())
                .isEqualTo(new AtividadeLicenciamento("Agropecuária", "Criação de gado"));
    }

    @Test
    void requerimentoSemNenhumBlocoTrazObjetoVazio() {
        assertThat(siga.objeto(105)).isEqualTo(ObjetoDoRequerimento.vazio());
    }

    // --- prioridade

    @Test
    void doisCriteriosEhPrioritarioComAsDescricoes() {
        PrioridadeAtendimento prioridade = siga.prioridade(101);

        assertThat(prioridade.tipo()).isEqualTo(PrioridadeAtendimento.Tipo.PRIORITARIO);
        assertThat(prioridade.criterios()).extracting(CriterioPrioridade::codigo).containsExactly(2, 5);
        assertThat(prioridade.criterios().get(1).descricao()).isEqualTo("Pessoa com doença grave");
    }

    @Test
    void semCriteriosEhNormalMesmoComCabecalhoDePrioridade() {
        assertThat(siga.prioridade(102).tipo()).isEqualTo(PrioridadeAtendimento.Tipo.NORMAL);
        assertThat(siga.prioridade(103).tipo()).isEqualTo(PrioridadeAtendimento.Tipo.NORMAL);
        assertThat(siga.prioridade(103).criterios()).isEmpty();
    }

    // --- falhas: mesma tabela da S1 (503 ou 500), sem estado parcial

    @Test
    void conexaoRecusadaViraSigaIndisponivel() {
        try (HikariDataSource fora = SigaOracleTeste.dataSourceInacessivel()) {
            ObjetoEPrioridadeDoSigaJdbc indisponivel = new ObjetoEPrioridadeDoSigaJdbc(new NamedParameterJdbcTemplate(fora));

            assertThatThrownBy(() -> indisponivel.objeto(101)).isInstanceOf(SigaIndisponivelException.class);
            assertThatThrownBy(() -> indisponivel.prioridade(101)).isInstanceOf(SigaIndisponivelException.class);
        }
    }

    @Test
    void faltaDeGrantViraErroInterno() {
        try (HikariDataSource semGrant = SigaOracleTeste.dataSource(SigaOracleTeste.SEM_PERMISSAO,
                SigaOracleTeste.SENHA_SEM_PERMISSAO)) {
            ObjetoEPrioridadeDoSigaJdbc sem = new ObjetoEPrioridadeDoSigaJdbc(new NamedParameterJdbcTemplate(semGrant));

            assertThatThrownBy(() -> sem.objeto(101)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> sem.prioridade(101)).isInstanceOf(IllegalStateException.class);
        }
    }
}
