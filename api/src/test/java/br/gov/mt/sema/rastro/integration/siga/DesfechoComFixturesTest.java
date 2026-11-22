package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.Aviso;
import br.gov.mt.sema.rastro.tramitacao.domain.AvaliarDesfecho;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoBruto;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CodigoDoAviso;
import br.gov.mt.sema.rastro.tramitacao.domain.DesfechoDoCaminho;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.Reconciliar;
import br.gov.mt.sema.rastro.tramitacao.domain.ReconstruirCaminho;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDoConfrontoDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Finais do caminho (S8, bloco 4) sobre as fixtures, com a situação gravada no Licenciamento fictício: todos os finais do
 * Hídrico (HF01 a HF04) e do LAC (LC01, LF01 a LF05), o concluído pela publicação (HC01) e a combinação divergente.
 */
@Testcontainers(disabledWithoutDocker = true)
class DesfechoComFixturesTest {

    private static final CatalogoDeEtapas CATALOGO = CatalogoDeEtapasDeTeste.padrao();
    private static final AvaliarDesfecho AVALIAR = new AvaliarDesfecho(new Reconciliar(CatalogoDoConfrontoDeTeste.padrao()));

    private static HikariDataSource leitura;
    private static CamundaPosicaoJdbc camunda;

    @BeforeAll
    static void abrir() {
        assertThat(SigaOracleTeste.CENARIOS_S8).isNotEmpty();
        leitura = SigaOracleTeste.dataSourceLeitura();
        camunda = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(leitura), 10);
    }

    @AfterAll
    static void fechar() {
        leitura.close();
    }

    private static DesfechoDoCaminho desfecho(long id, Optional<String> situacao) {
        String chave = id >= LC01 ? "procLicenciamentoLac" : "procOutorgaAguaSupSub";
        ModeloDeFluxo modelo = CATALOGO.modelo(chave).orElseThrow();
        CaminhoBruto bruto = camunda.lerCaminho(id, chave);
        return AVALIAR.avaliar(bruto, ReconstruirCaminho.reconstruir(bruto, modelo, java.util.Map.of()), modelo, situacao);
    }

    private static DesfechoDoCaminho desfecho(long id) {
        return desfecho(id, camunda.requerimento(id).map(r -> r.situacao()));
    }

    private static List<CodigoDoAviso> codigos(DesfechoDoCaminho d) {
        return d.avisos().stream().map(Aviso::codigo).toList();
    }

    @ParameterizedTest(name = "{0}: {2}")
    @CsvSource({
            "HF01, 8021, Concluído, ",
            "HF02, 8022, Cancelado pelo cadastrante, ",
            "HF03, 8023, Expirado no cadastro, ",
            "HF04, 8024, Encerrado por taxa não paga no prazo, FLUXO_ENCERRADO_SEM_STATUS",
            "LC01, 8051, Concluído, ",
            "LF01, 8061, Cancelado pelo cadastrante, ",
            "LF02, 8062, Expirado no cadastro, ",
            "LF03, 8063, Expirado na correção, EXPIRADO_NA_CORRECAO_LAC",
            "LF04, 8064, Cancelado por falta de pagamento, ",
            "LF05, 8065, Arquivado (termo não enviado no prazo), "})
    void finais_com_o_rotulo_do_evento_e_o_aviso_da_situacao(String cenario, long id, String rotulo, CodigoDoAviso aviso) {
        DesfechoDoCaminho d = desfecho(id);
        assertThat(d.estado()).as(cenario).isEqualTo(DesfechoDoCaminho.Estado.ENCERRADO);
        assertThat(d.bolaPreta()).as(cenario).isTrue();
        assertThat(d.rotulo()).as(cenario).contains(rotulo);
        assertThat(d.quando()).as(cenario).isPresent();
        if (aviso == null) {
            assertThat(d.avisos()).as(cenario).isEmpty();
        } else {
            assertThat(codigos(d)).as(cenario).containsExactly(aviso);
        }
    }

    @Test
    void hidrico_taxa_vencida_mantem_a_situacao_do_licenciamento() {
        assertThat(camunda.requerimento(HF04).orElseThrow().situacao()).isEqualTo("AGUARDANDO_PAGAMENTO_DE_TAXAS");
        assertThat(desfecho(HF04).rotulo()).contains("Encerrado por taxa não paga no prazo");
    }

    @Test
    void lac_expirado_no_cadastro_e_na_correcao_tem_a_mesma_situacao_e_rotulos_diferentes() {
        assertThat(camunda.requerimento(LF02).orElseThrow().situacao())
                .isEqualTo(camunda.requerimento(LF03).orElseThrow().situacao());
        assertThat(desfecho(LF02).rotulo()).contains("Expirado no cadastro");
        assertThat(desfecho(LF03).rotulo()).contains("Expirado na correção");
    }

    @Test
    void concluido_pela_publicacao_fica_concluido_sem_bola_preta() {
        DesfechoDoCaminho d = desfecho(HC01);
        assertThat(d.estado()).isEqualTo(DesfechoDoCaminho.Estado.CONCLUIDO);
        assertThat(d.bolaPreta()).isFalse();
        assertThat(d.rotulo()).contains("Concluído");
        assertThat(codigos(d)).containsExactly(CodigoDoAviso.CONCLUIDO_ANTES_DA_VERIFICACAO);
    }

    @Test
    void em_andamento_nao_tem_final() {
        for (long id : new long[] {HC02, HC07, HC13, LC03}) {
            DesfechoDoCaminho d = desfecho(id);
            assertThat(d.estado()).as("" + id).isEqualTo(DesfechoDoCaminho.Estado.EM_ANDAMENTO);
            assertThat(d.bolaPreta()).as("" + id).isFalse();
        }
    }

    @Test
    void combinacao_divergente() {
        DesfechoDoCaminho d = desfecho(HF01, Optional.of("EM_ANALISE"));
        assertThat(d.rotulo()).contains("Concluído");
        assertThat(codigos(d)).containsExactly(CodigoDoAviso.FINAL_E_SITUACAO_DIVERGEM);
    }

    @Test
    void sem_instancia() {
        assertThat(desfecho(PB01).estado()).isEqualTo(DesfechoDoCaminho.Estado.SEM_INSTANCIA);
    }
}
