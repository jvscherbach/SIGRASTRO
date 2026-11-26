package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import br.gov.mt.sema.rastro.consulta.domain.CriterioBusca;
import br.gov.mt.sema.rastro.consulta.domain.NumeroRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Pagina;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.ResumoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.SituacaoRequerimento;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RequerimentosDoSigaJdbcTest {

    private static HikariDataSource dataSource;
    private static RequerimentosDoSigaJdbc siga;

    @BeforeAll
    static void abrir() {
        dataSource = SigaOracleTeste.dataSourceLeitura();
        siga = new RequerimentosDoSigaJdbc(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
    }

    private static CriterioBusca criterio(String tipo, String numero, String cpfCnpj, String nome) {
        return CriterioBusca.de(tipo, numero, cpfCnpj, nome, null, null);
    }

    // --- busca pontual

    @Test
    void buscaPorNumeroDeRequerimentoTrazOsDadosDoRequerimento() {
        Requerimento r = siga.buscarPorNumeroDeRequerimento(NumeroRequerimento.de("3331/2025")).orElseThrow();

        assertThat(r.id()).isEqualTo(101);
        assertThat(r.numeroRequerimento()).isEqualTo("3331/2025");
        assertThat(r.numeroProcesso()).isEqualTo("2825/2025");
        assertThat(r.identificacao().rotulo()).isEqualTo("Processo");
        assertThat(r.situacao().tipo()).isEqualTo(SituacaoRequerimento.EM_ANALISE);
        assertThat(r.abertura()).isEqualTo(LocalDateTime.of(2025, 3, 31, 10, 12));
        assertThat(r.atualizacao()).isEqualTo(LocalDateTime.of(2025, 4, 29, 9, 14));
        assertThat(r.interessado().nome()).isEqualTo("Associação Residencial Vale Verde");
        assertThat(r.interessado().cpfCnpj()).isEqualTo("11222333000181");
        assertThat(r.tipoProcesso()).isEqualTo("Uso de Recursos Hídricos");
        assertThat(r.tipoRequerimento()).isEqualTo("Requerimento de Outorga");
    }

    @Test
    void requerimentoSemTipoDeRequerimentoTrazNulo() {
        assertThat(siga.buscarPorId(102).orElseThrow().tipoRequerimento()).isNull();
    }

    @Test
    void buscaPorNumeroDeProcessoUnicoDevolveORequerimentoCorrespondente() {
        Pagina<ResumoRequerimento> pagina = siga.buscarPorCriterios(criterio("PROCESSO", "2825/2025", null, null));

        assertThat(pagina.itens()).extracting(ResumoRequerimento::id).containsExactly(101L);
    }

    @Test
    void numeroDeProcessoRepetidoDevolveTodosOsRequerimentosPorAberturaDecrescente() {
        Pagina<ResumoRequerimento> pagina = siga.buscarPorCriterios(criterio("PROCESSO", "445/2022", null, null));

        assertThat(pagina.total()).isEqualTo(2);
        assertThat(pagina.itens()).extracting(ResumoRequerimento::id).containsExactly(402L, 401L);
    }

    @Test
    void numeroDeProcessoRepetidoComRestricaoDevolveSoODoInteressado() {
        CriterioBusca porProcesso = criterio("PROCESSO", "445/2022", null, null);

        assertThat(siga.buscarPorCriterios(porProcesso.comRestricao("55555555555")).itens())
                .extracting(ResumoRequerimento::id).containsExactly(401L);
        assertThat(siga.buscarPorCriterios(porProcesso.comRestricao("11222333000181")).total()).isZero();
        // Verificação de existência usada pelo caso de uso para decidir entre 403 e 404.
        assertThat(siga.buscarPorCriterios(porProcesso.comRestricao("11222333000181").soParaVerificarExistencia())
                .total()).isEqualTo(2);
    }

    @Test
    void numeroDeRequerimentoNaoEncontrouDevolveVazio() {
        assertThat(siga.buscarPorNumeroDeRequerimento(NumeroRequerimento.de("9999/2020"))).isEmpty();
        assertThat(siga.buscarPorCriterios(criterio("PROCESSO", "3331/2025", null, null)).total()).isZero();
        assertThat(siga.buscarPorId(-1)).isEmpty();
    }

    @Test
    void requerimentoSemProcessoEhIdentificadoPeloNumeroDoRequerimento() {
        Requerimento r = siga.buscarPorId(102).orElseThrow();

        assertThat(r.numeroProcesso()).isNull();
        assertThat(r.identificacao().rotulo()).isEqualTo("Requerimento");
        assertThat(r.identificacao().protocolado()).isFalse();
    }

    @Test
    void requerimentoSemInteressadoNaoQuebra() {
        Requerimento r = siga.buscarPorId(105).orElseThrow();

        assertThat(r.interessado()).isNull();
        assertThat(r.atualizacao()).isNull();
    }

    @Test
    void situacaoQueOVistasNaoConheceViraDesconhecidaComOTextoCru() {
        Requerimento r = siga.buscarPorId(104).orElseThrow();

        assertThat(r.situacao().tipo()).isEqualTo(SituacaoRequerimento.DESCONHECIDA);
        assertThat(r.situacao().descricao()).isEqualTo("SITUACAO_NOVA_DO_SIGA");
    }

    // --- lista

    @Test
    void listaPorCpfCnpjFormatadoEncontraPorqueODominioNormalizaParaDigitos() {
        Pagina<ResumoRequerimento> pagina = siga.buscarPorCriterios(criterio(null, null, "45.678.901/0001-23", null));

        assertThat(pagina.itens()).extracting(ResumoRequerimento::id).containsExactly(102L);
    }

    @Test
    void listaPorCpfCnpjSoComDigitosComparaDiretoComOBanco() {
        Pagina<ResumoRequerimento> pagina = siga.buscarPorCriterios(criterio(null, null, "12345678909", null));

        assertThat(pagina.itens()).extracting(ResumoRequerimento::id).containsExactly(104L);
    }

    @Test
    void listaPorNomeParcialSemDiferenciarMaiusculas() {
        Pagina<ResumoRequerimento> pagina = siga.buscarPorCriterios(criterio(null, null, null, "boa ESPERANÇA"));

        assertThat(pagina.itens()).extracting(ResumoRequerimento::id).containsExactly(102L);
    }

    @Test
    void percentEUnderscoreDigitadosSaoLiteraisNoNome() {
        assertThat(siga.buscarPorCriterios(criterio(null, null, null, "100%")).itens())
                .extracting(ResumoRequerimento::id).containsExactly(103L);
        assertThat(siga.buscarPorCriterios(criterio(null, null, null, "Rio_Claro")).itens())
                .extracting(ResumoRequerimento::id).containsExactly(103L);
        // Se % e _ fossem curingas, estes dois casariam com "100% Rio_Claro".
        assertThat(siga.buscarPorCriterios(criterio(null, null, null, "Rio%Claro")).itens()).isEmpty();
        assertThat(siga.buscarPorCriterios(criterio(null, null, null, "Rio.Claro")).itens()).isEmpty();
        assertThat(siga.buscarPorCriterios(criterio(null, null, null, "Rio_Cl_ro")).itens()).isEmpty();
    }

    @Test
    void listaPaginadaTrazTotalEOrdemPorAberturaDecrescente() {
        CriterioBusca primeira = CriterioBusca.de(null, null, "11.222.333/0001-81", null, 0, 10);
        CriterioBusca ultima = CriterioBusca.de(null, null, "11.222.333/0001-81", null, 2, 10);

        Pagina<ResumoRequerimento> p0 = siga.buscarPorCriterios(primeira);
        Pagina<ResumoRequerimento> p2 = siga.buscarPorCriterios(ultima);

        assertThat(p0.total()).isEqualTo(26); // 101 + 25 gerados
        assertThat(p0.itens()).hasSize(10);
        assertThat(p0.itens().get(0).id()).isEqualTo(101); // abertura mais recente (2025)
        assertThat(p0.itens().get(1).numeroRequerimento()).isEqualTo("25/2023");
        assertThat(p2.itens()).hasSize(6);
        assertThat(p2.itens().get(5).numeroRequerimento()).isEqualTo("1/2023");
    }

    @Test
    void numeroEOutrosCriteriosCombinam() {
        Pagina<ResumoRequerimento> acha = siga.buscarPorCriterios(
                criterio("REQUERIMENTO", "3331/2025", "11.222.333/0001-81", null));
        Pagina<ResumoRequerimento> naoAcha = siga.buscarPorCriterios(
                criterio("PROCESSO", "2825/2025", "45678901000123", null));

        assertThat(acha.itens()).extracting(ResumoRequerimento::id).containsExactly(101L);
        assertThat(naoAcha.total()).isZero();
    }

    @Test
    void restricaoDeInteressadoEAplicadaNoSqlEOTotalJaVemFiltrado() {
        CriterioBusca livre = criterio(null, null, null, "vale");
        CriterioBusca doDono = livre.comRestricao("11222333000181");
        CriterioBusca deOutro = livre.comRestricao("45678901000123");

        assertThat(siga.buscarPorCriterios(livre).total()).isEqualTo(26);
        assertThat(siga.buscarPorCriterios(doDono).total()).isEqualTo(26);
        Pagina<ResumoRequerimento> bloqueada = siga.buscarPorCriterios(deOutro);
        assertThat(bloqueada.total()).isZero();
        assertThat(bloqueada.itens()).isEmpty();
    }

    @Test
    void restricaoNaoVazaRequerimentosDeOutrosInteressadosNemComCriterioLargo() {
        CriterioBusca porNomeLargo = criterio(null, null, null, "ltda").comRestricao("11222333000181");

        assertThat(siga.buscarPorCriterios(porNomeLargo).total()).isZero();
    }

    // --- falhas

    @Test
    void conexaoRecusadaViraSigaIndisponivel() {
        try (HikariDataSource fora = SigaOracleTeste.dataSourceInacessivel()) {
            RequerimentosDoSigaJdbc indisponivel = new RequerimentosDoSigaJdbc(new NamedParameterJdbcTemplate(fora));

            assertThatThrownBy(() -> indisponivel.buscarPorId(101)).isInstanceOf(SigaIndisponivelException.class);
            assertThatThrownBy(() -> indisponivel.buscarPorCriterios(criterio(null, null, null, "vale")))
                    .isInstanceOf(SigaIndisponivelException.class);
        }
    }

    @Test
    void faltaDePermissaoNaTabelaViraErroInternoENaoIndisponibilidade() {
        try (HikariDataSource semGrant = SigaOracleTeste.dataSource(SigaOracleTeste.SEM_PERMISSAO,
                SigaOracleTeste.SENHA_SEM_PERMISSAO)) {
            RequerimentosDoSigaJdbc sem = new RequerimentosDoSigaJdbc(new NamedParameterJdbcTemplate(semGrant));

            assertThatThrownBy(() -> sem.buscarPorId(101))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining("REQUERIMENTO");
        }
    }

    @Test
    void usuarioDeLeituraNaoEscreveNoSiga() {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE SIGA2_LICENCIAMENTO.REQUERIMENTO SET SITUACAO_PROCESSO = 'CANCELADO' WHERE ID = 101",
                java.util.Map.of()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
