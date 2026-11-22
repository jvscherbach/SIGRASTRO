package br.gov.mt.sema.rastro.tramitacao.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import br.gov.mt.sema.rastro.tramitacao.application.port.CaminhoPercorrido;
import br.gov.mt.sema.rastro.tramitacao.application.port.HistoricoDoLicenciamento;
import br.gov.mt.sema.rastro.tramitacao.application.port.IdentificacaoDosResponsaveis;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.AtividadeBruta;
import br.gov.mt.sema.rastro.tramitacao.domain.Aviso;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoBruto;
import br.gov.mt.sema.rastro.tramitacao.domain.CodigoDoAviso;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificacaoDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.InstanciaDoHistorico;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoPlanoB;
import br.gov.mt.sema.rastro.tramitacao.domain.OrigemDaInstancia;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento.Estado;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDoConfrontoDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.MapaDeEventosDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

/**
 * Escolha da fonte da tramitação (S8, bloco 5): o plano B <b>só</b> quando a raiz não existe; Camunda fora ou lento é
 * {@code INDISPONIVEL} e o plano B nem é consultado. Sem banco: portas trocadas por dublês.
 */
class MontarTramitacaoTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 8, 0);
    private static final String CPF = "00000000005";

    private final AtomicInteger chamadasAoPlanoB = new AtomicInteger();
    private final ParametrosDoRastroDeTeste parametros = new ParametrosDoRastroDeTeste();
    private Optional<PosicaoNoFluxo.RequerimentoNoFluxo> requerimento =
            Optional.of(new PosicaoNoFluxo.RequerimentoNoFluxo("RECURSO_HIDRICO", "EM_ANALISE"));
    private RuntimeException falhaNoRequerimento;
    private CaminhoPercorrido camunda =(id, chave) -> CaminhoBruto.semInstancia();
    private HistoricoDoLicenciamento licenciamento = id -> {
        chamadasAoPlanoB.incrementAndGet();
        return new LeituraDoPlanoB(Optional.of("CONCLUIDO"), new LeituraDoPlanoB.Datas(T0, null, null, null, null),
                List.of(new LeituraDoPlanoB.Evento("CRIACAO_REQUERIMENTO", T0)), false);
    };
    private IdentificacaoDosResponsaveis identificacao = (id, ids) -> Map.of(
            IdentificadorDoResponsavel.de(CPF).orElseThrow(),
            new IdentificacaoDoResponsavel(Optional.of("Servidor Exemplo 05"), false));

    private TramitacaoDoRequerimento montar() {
        PosicaoNoFluxo posicao = new PosicaoNoFluxo() {
            @Override
            public Optional<RequerimentoNoFluxo> requerimento(long idRequerimento) {
                if (falhaNoRequerimento != null) {
                    throw falhaNoRequerimento;
                }
                return requerimento;
            }

            @Override
            public LeituraDoFluxo ler(long idRequerimento, String chaveDoModelo) {
                throw new AssertionError("a tramitação não lê a posição");
            }
        };
        return new MontarTramitacao(posicao, camunda, licenciamento, identificacao, CatalogoDeEtapasDeTeste.padrao(),
                MapaDeEventosDeTeste.padrao(), CatalogoDoConfrontoDeTeste.padrao()).montar(8001);
    }

    private static CaminhoBruto comAnalise() {
        return comAnalise(false);
    }

    private static CaminhoBruto comAnalise(boolean truncada) {
        InstanciaDoHistorico raiz = new InstanciaDoHistorico("r", OrigemDaInstancia.RAIZ, "procOutorgaAguaSupSub", T0, null,
                null, "ACTIVE", null, truncada, 1);
        IdentificadorDoResponsavel id = IdentificadorDoResponsavel.de(CPF).orElseThrow();
        AtividadeBruta revisao = new AtividadeBruta("r", OrigemDaInstancia.RAIZ, "r-a000001", null, "r",
                "taskRevisarParecerTecnico", null, "userTask", T0, null, Optional.of(id), null,
                Optional.of(new AtividadeBruta.TarefaBruta("taskRevisarParecerTecnico", Optional.of(id), T0, null,
                        AtividadeBruta.MotivoDaTarefa.ABERTA)));
        return new CaminhoBruto(List.of(raiz), 2, List.of(revisao), List.of(), List.of());
    }

    private static List<CodigoDoAviso> codigos(TramitacaoDoRequerimento t) {
        return t.avisos().stream().map(Aviso::codigo).toList();
    }

    @Test
    void sem_instancia_usa_o_plano_b_aproximado_com_o_selo() {
        TramitacaoDoRequerimento t = montar();
        assertThat(t.estado()).isEqualTo(Estado.SEM_INSTANCIA);
        assertThat(t.aproximado()).isTrue();
        assertThat(t.planoB()).hasValueSatisfying(h -> assertThat(h.etapas()).hasSize(1));
        assertThat(t.situacao()).contains("CONCLUIDO");
        assertThat(t.caminho().passagens()).isEmpty();
        assertThat(t.desfecho()).isEmpty();
        assertThat(codigos(t)).containsExactly(CodigoDoAviso.HISTORICO_APROXIMADO);
        assertThat(chamadasAoPlanoB).hasValue(1);
    }

    @Test
    void camunda_fora_ou_lento_e_indisponivel_e_o_plano_b_nem_e_consultado() {
        for (RuntimeException falha : List.of(new SigaIndisponivelException(), new QueryTimeoutException("lento"))) {
            camunda = (id, chave) -> {
                throw falha;
            };
            TramitacaoDoRequerimento t = montar();
            assertThat(t.estado()).isEqualTo(Estado.INDISPONIVEL);
            assertThat(t.aproximado()).isFalse();
            assertThat(t.planoB()).isEmpty();
            assertThat(t.situacao()).contains("EM_ANALISE");
            assertThat(t.modelo()).contains("procOutorgaAguaSupSub");
        }
        assertThat(chamadasAoPlanoB).hasValue(0);
    }

    @Test
    void falha_no_proprio_plano_b_tambem_e_indisponivel() {
        licenciamento = id -> {
            throw new SigaIndisponivelException();
        };
        assertThat(montar().estado()).isEqualTo(Estado.INDISPONIVEL);
    }

    @Test
    void falha_ao_ler_o_requerimento_e_indisponivel() {
        falhaNoRequerimento = new SigaIndisponivelException();
        camunda = (id, chave) -> {
            throw new AssertionError("não deveria chegar ao Camunda");
        };
        TramitacaoDoRequerimento t = montar();
        assertThat(t.estado()).isEqualTo(Estado.INDISPONIVEL);
        assertThat(t.situacao()).isEmpty();
        assertThat(chamadasAoPlanoB).hasValue(0);
    }

    @Test
    void tipo_fora_do_catalogo_e_nao_suportado_sem_ir_ao_camunda() {
        requerimento = Optional.of(new PosicaoNoFluxo.RequerimentoNoFluxo("LICENCIAMENTO_TRIFASICO_FICTICIO", "EM_ANALISE"));
        camunda = (id, chave) -> {
            throw new AssertionError("não deveria chegar ao Camunda");
        };
        TramitacaoDoRequerimento t = montar();
        assertThat(t.estado()).isEqualTo(Estado.NAO_SUPORTADO);
        assertThat(chamadasAoPlanoB).hasValue(0);
    }

    @Test
    void com_instancia_reconstroi_o_caminho_e_nunca_usa_o_plano_b() {
        camunda = (id, chave) -> comAnalise();
        TramitacaoDoRequerimento t = montar();
        assertThat(t.estado()).isEqualTo(Estado.ATIVO);
        assertThat(t.aproximado()).isFalse();
        assertThat(t.caminho().passagens()).singleElement().satisfies(p -> {
            assertThat(p.atual()).isTrue();
            assertThat(p.responsaveis().getFirst().nome()).contains("Servidor Exemplo 05");
        });
        assertThat(codigos(t)).contains(CodigoDoAviso.VARIAS_INSTANCIAS);
        assertThat(chamadasAoPlanoB).hasValue(0);
        assertThat(t.toString()).doesNotContain(CPF);
    }

    @Test
    void historico_acima_do_limite_de_linhas_traz_o_aviso_de_truncado_e_sem_ele_nao() {
        camunda = (id, chave) -> comAnalise(true);
        TramitacaoDoRequerimento truncado = montar();
        assertThat(truncado.caminho().truncado()).isTrue();
        assertThat(codigos(truncado)).contains(CodigoDoAviso.HISTORICO_TRUNCADO);

        camunda = (id, chave) -> comAnalise(false);
        assertThat(codigos(montar())).doesNotContain(CodigoDoAviso.HISTORICO_TRUNCADO);
    }

    @Test
    void montar_guarda_os_nomes_e_falha_na_identificacao_nao_derruba() {
        // O parâmetro de nome vale depois do cache, em LerTramitacao (bloco 7); montar sempre resolve o nome.
        camunda = (id, chave) -> comAnalise();
        parametros.mostrarNome = false;
        assertThat(montar().caminho().passagens().getFirst().responsaveis().getFirst().nome())
                .contains("Servidor Exemplo 05");
        identificacao = (id, ids) -> {
            throw new SigaIndisponivelException();
        };
        TramitacaoDoRequerimento t = montar();
        assertThat(t.estado()).isEqualTo(Estado.ATIVO);
        assertThat(t.caminho().passagens().getFirst().responsaveis().getFirst().papel())
                .isEqualTo("Responsável não identificado");
    }
}
