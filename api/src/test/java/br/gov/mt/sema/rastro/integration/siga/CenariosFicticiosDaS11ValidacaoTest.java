package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS11.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** A tabela do cenário da caixa (S11, bloco 1) é coerente com o catálogo de etapas e com a regra do aviso. Sem Docker. */
class CenariosFicticiosDaS11ValidacaoTest {

    private static final Pattern ESTADO_COM_ANALISTA = Pattern.compile("COM_ANALISTA\\((.+)\\)");

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CamundaFicticioBuilder camunda = new CamundaFicticioBuilder(jdbc, CatalogoDeEtapasDeTeste.padrao());

    @Test
    void osIdsDosCasosSaoUnicosEFicamNaFaixaDaS11() {
        assertThat(ids()).hasSize(17).doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).isBetween(15001L, 15099L));
        assertThat(CASOS.stream().map(Caso::codigo)).doesNotHaveDuplicates();
        assertThat(VOLUME_PRIMEIRO).isGreaterThan(15099);
        assertThat(VOLUME_PRIMEIRO + VOLUME_TOTAL - 1).isEqualTo(17200);
        assertThat(VOLUME_POR_ANALISTA * 3).isEqualTo(VOLUME_TOTAL);
    }

    @Test
    void osEstadosUsamIdsDoCatalogoDeEtapas() {
        Set<String> idsDoCatalogo = CatalogoDeEtapasDeTeste.padrao().modelo(Fluxo.HIDRICO.chave()).orElseThrow().etapas().stream()
                .map(Etapa::id).collect(Collectors.toSet());
        for (Caso caso : CASOS) {
            for (String estado : List.of(caso.estadoEsperado(), caso.estadoNoUltimoVisto())) {
                Matcher m = ESTADO_COM_ANALISTA.matcher(estado);
                if (m.matches()) {
                    assertThat(idsDoCatalogo).as("%s: %s", caso.codigo(), estado).contains(m.group(1));
                } else {
                    assertThat(Set.of(COM_INTERESSADO, FORA, INEXISTENTE)).as(caso.codigo()).contains(estado);
                }
            }
        }
    }

    @Test
    void oAvisoSoExisteQuandoOEstadoAtualEDoAnalistaEOVistoNaoEra() {
        for (Caso caso : CASOS) {
            boolean vistoNaPosse = ESTADO_COM_ANALISTA.matcher(caso.estadoNoUltimoVisto()).matches();
            assertThat(caso.aviso()).as(caso.codigo()).isEqualTo(caso.daPosse() && !vistoNaPosse);
            assertThat(caso.origem() != null).as("origem só quando há aviso: %s", caso.codigo()).isEqualTo(caso.aviso());
        }
        assertThat(CASOS.stream().filter(Caso::aviso).map(Caso::codigo)).containsExactly("CX05", "CX06", "CX07", "CX08", "CX09",
                "CX17");
    }

    @Test
    void oCenarioCobreOsEstadosAsOrigensEOsInteressadosPedidos() {
        assertThat(CASOS.stream().map(Caso::estadoEsperado).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(COM_ANALISTA_ANALISE, COM_ANALISTA_REVISAO, COM_INTERESSADO, FORA);
        assertThat(CASOS.stream().map(Caso::origem).filter(o -> o != null).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(VOLTOU_DA_CORRECAO, VOLTOU_DA_VALIDACAO, VOLTOU_DA_EMISSAO, VOLTOU_DA_REVISAO,
                        NOVO_NA_CAIXA);
        // dois requerimentos com o mesmo interessado, de analistas diferentes
        assertThat(CASOS.stream().filter(c -> c.interessado().equals(NOME_DO_P1)).map(Caso::analista).collect(Collectors.toSet()))
                .contains("A1", "A3");
        // pessoa e empresa (razão social)
        assertThat(CASOS.stream().map(Caso::interessado)).contains(NOME_DO_P2, NOME_DO_P4);
        // três analistas com requerimentos; o quarto não tem CPF
        assertThat(CASOS.stream().map(Caso::analista).filter(a -> a != null).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("A1", "A2", "A3");
        assertThat(A4).isNull();
    }

    @Test
    void umRequerimentoDeAnoAnteriorFicaNaPosseDeA1() {
        assertThat(a(400).toLocalDate().getYear()).isEqualTo(2027);
        assertThat(CASOS.stream().filter(c -> c.id() == CX14)).singleElement().satisfies(c -> {
            assertThat(c.daPosse()).isTrue();
            assertThat(c.analista()).isEqualTo("A1");
            assertThat(c.diasNaEtapa()).isEqualTo(400);
        });
    }

    @Test
    void osDiasNaEtapaVemDoRelogioFixoEDeA() {
        assertThat(LocalDate.now(RELOGIO)).isEqualTo(HOJE);
        assertThat(RELOGIO.getZone().getId()).isEqualTo("America/Cuiaba");
        assertThat(a(20).toLocalDate()).isEqualTo(HOJE.minusDays(20));
        // o "agora" é 12h e a(n) é 9h de n dias antes: n dias e 3 horas completos
        long horas = java.time.Duration.between(a(20), HOJE.atTime(12, 0)).toHours();
        assertThat(horas / 24).isEqualTo(20);
    }

    @Test
    void nenhumDadoRealSoNomesEvidentesECpfsInvalidos() {
        for (String cpf : List.of(A1, A2, A3)) {
            assertThat(cpf).matches("000000000(0[1-9]|1[0-2])");
        }
        for (String nome : List.of(NOME_DO_A1, NOME_DO_A2, NOME_DO_A3, NOME_DO_A4, NOME_DO_P1, NOME_DO_P2, NOME_DO_P3, NOME_DO_P4,
                NOME_DO_P_VOLUME)) {
            assertThat(nome).contains("Exemplo");
        }
        assertThat(A1).isNotEqualTo(A2);
    }

    @Test
    void osDocumentosDosInteressadosSaoInvalidos() {
        for (String documento : List.of("00000000101", "00000000000102", "00000000103", "00000000000104", "00000000105")) {
            assertThat(br.gov.mt.sema.rastro.acesso.domain.CpfCnpjDoUsuario.valido(documento)).as(documento).isFalse();
        }
    }

    @Test
    void oConstrutorRecusaPessoaComNomeOuDocumentoQueNaoSejaFicticio() {
        assertThatThrownBy(() -> camunda.pessoaFicticia(1, "Fulano de Tal", "00000000101"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.pessoaFicticia(1, "Interessado Exemplo", "12345678909"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> camunda.pessoaFicticia(1, "Interessado Exemplo", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oInteressadoDoProcessoEFluente() {
        var processo = camunda.processo(Fluxo.HIDRICO, 1);
        assertThat(processo.comInteressado(P2)).isSameAs(processo);
    }
}
