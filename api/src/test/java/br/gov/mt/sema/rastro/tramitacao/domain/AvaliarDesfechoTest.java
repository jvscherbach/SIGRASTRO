package br.gov.mt.sema.rastro.tramitacao.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDoConfrontoDeTeste;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Finais e divergências do final (S8, bloco 4), sem banco. Os cenários das fixtures ficam em {@code DesfechoComFixturesTest}. */
class AvaliarDesfechoTest {

    private static final CatalogoDeEtapas ETAPAS = CatalogoDeEtapasDeTeste.padrao();
    private static final AvaliarDesfecho AVALIAR = new AvaliarDesfecho(new Reconciliar(CatalogoDoConfrontoDeTeste.padrao()));
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 8, 0);

    private static ModeloDeFluxo modelo(String chave) {
        return ETAPAS.modelo(chave).orElseThrow();
    }

    private static CaminhoBruto encerrada(String chave, String fim) {
        InstanciaDoHistorico raiz = new InstanciaDoHistorico("r", OrigemDaInstancia.RAIZ, chave, T0, T0.plusDays(30), fim,
                "COMPLETED", null, false, 0);
        return new CaminhoBruto(List.of(raiz), 1, List.of(), List.of(), List.of());
    }

    private static DesfechoDoCaminho encerradoEm(String chave, String fim, String situacao) {
        CaminhoBruto bruto = encerrada(chave, fim);
        return AVALIAR.avaliar(bruto, ReconstruirCaminho.reconstruir(bruto, modelo(chave), Map.of()), modelo(chave),
                Optional.ofNullable(situacao));
    }

    private static List<CodigoDoAviso> codigos(DesfechoDoCaminho d) {
        return d.avisos().stream().map(Aviso::codigo).toList();
    }

    @ParameterizedTest(name = "{1} com {2}")
    @CsvSource({
            "procOutorgaAguaSupSub, Event_1wiuc48, CONCLUIDO, Concluído, ",
            "procOutorgaAguaSupSub, Event_1kq2k0z, CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE, Cancelado pelo cadastrante, ",
            "procOutorgaAguaSupSub, Event_0abm55w, CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO, Expirado no cadastro, ",
            "procOutorgaAguaSupSub, Event_0h1oige, AGUARDANDO_PAGAMENTO_DE_TAXAS, Encerrado por taxa não paga no prazo, FLUXO_ENCERRADO_SEM_STATUS",
            "procOutorgaAguaSupSub, Event_0h1oige, CANCELADO_POR_FALTA_DE_PAGAMENTO, Encerrado por taxa não paga no prazo, ",
            "procLicenciamentoLac, evtProcessoConcluido, CONCLUIDO, Concluído, ",
            "procLicenciamentoLac, evtFimRequerimentoAPedidoCadastrante, CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE, Cancelado pelo cadastrante, ",
            "procLicenciamentoLac, evtEncerrarRequerimentoPrazoCadExpirado, CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO, Expirado no cadastro, ",
            "procLicenciamentoLac, evtEncerrarProcessoPrazoCorrecaoExpirado, CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO, Expirado na correção, EXPIRADO_NA_CORRECAO_LAC",
            "procLicenciamentoLac, evtEncerrarProcessoFaltaPagto, CANCELADO_POR_FALTA_DE_PAGAMENTO, Cancelado por falta de pagamento, ",
            "procLicenciamentoLac, Event_0x7hz1p, ARQUIVADO_DEFINITIVAMENTE, Arquivado (termo não enviado no prazo), "})
    void rotulo_vem_do_evento_de_fim_e_a_situacao_so_gera_aviso(String chave, String fim, String situacao, String rotulo,
            CodigoDoAviso aviso) {
        DesfechoDoCaminho d = encerradoEm(chave, fim, situacao);
        assertThat(d.estado()).isEqualTo(DesfechoDoCaminho.Estado.ENCERRADO);
        assertThat(d.bolaPreta()).isTrue();
        assertThat(d.rotulo()).contains(rotulo);
        assertThat(d.quando()).contains(T0.plusDays(30));
        if (aviso == null) {
            assertThat(d.avisos()).isEmpty();
        } else {
            assertThat(codigos(d)).containsExactly(aviso);
        }
    }

    @Test
    void mesma_situacao_no_lac_da_rotulos_diferentes_pelo_evento() {
        String situacao = "CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO";
        assertThat(encerradoEm("procLicenciamentoLac", "evtEncerrarRequerimentoPrazoCadExpirado", situacao).rotulo())
                .contains("Expirado no cadastro");
        assertThat(encerradoEm("procLicenciamentoLac", "evtEncerrarProcessoPrazoCorrecaoExpirado", situacao).rotulo())
                .contains("Expirado na correção");
    }

    @Test
    void combinacao_fora_da_tabela_gera_final_e_situacao_divergem_sem_trocar_o_rotulo() {
        DesfechoDoCaminho d = encerradoEm("procOutorgaAguaSupSub", "Event_1wiuc48", "EM_ANALISE");
        assertThat(d.rotulo()).contains("Concluído");
        assertThat(codigos(d)).containsExactly(CodigoDoAviso.FINAL_E_SITUACAO_DIVERGEM);
        assertThat(d.avisos().getFirst().texto()).contains("'EM_ANALISE'");
    }

    @Test
    void evento_de_fim_fora_do_catalogo_e_encerrado_sem_bola_preta() {
        DesfechoDoCaminho d = encerradoEm("procOutorgaAguaSupSub", "Event_FicticioDesconhecido", "CONCLUIDO");
        assertThat(d.estado()).isEqualTo(DesfechoDoCaminho.Estado.ENCERRADO);
        assertThat(d.bolaPreta()).isFalse();
        assertThat(d.rotulo()).isEmpty();
        assertThat(codigos(d)).containsExactly(CodigoDoAviso.ETAPA_NAO_MAPEADA);
    }

    @Test
    void sem_situacao_nao_acusa_divergencia() {
        assertThat(encerradoEm("procOutorgaAguaSupSub", "Event_0h1oige", null).avisos()).isEmpty();
    }

    @Test
    void arquivado_temporariamente_e_aviso_neutro_mesmo_em_final_que_nao_o_aceita() {
        DesfechoDoCaminho d = encerradoEm("procOutorgaAguaSupSub", "Event_1wiuc48", "ARQUIVADO_TEMPORARIAMENTE");
        assertThat(codigos(d)).containsExactly(CodigoDoAviso.SITUACAO_ARQUIVADO_TEMPORARIAMENTE);
        assertThat(d.avisos().getFirst().gravidade()).isEqualTo(Aviso.Gravidade.INFO);
    }

    private static DesfechoDoCaminho ativa(String idTecnico, String situacao) {
        InstanciaDoHistorico raiz = new InstanciaDoHistorico("r", OrigemDaInstancia.RAIZ, "procOutorgaAguaSupSub", T0, null,
                null, "ACTIVE", null, false, 2);
        AtividadeBruta publicacao = new AtividadeBruta("r", OrigemDaInstancia.RAIZ, "r-a000001", null, "r",
                "taskRegistarComprovantePublicacao", null, "userTask", T0, T0.plusDays(4), Optional.empty(), null,
                Optional.empty());
        AtividadeBruta aberta = new AtividadeBruta("r", OrigemDaInstancia.RAIZ, "r-a000002", null, "r", idTecnico, null,
                "userTask", T0.plusDays(4), null, Optional.empty(), null, Optional.empty());
        CaminhoBruto bruto = new CaminhoBruto(List.of(raiz), 1, List.of(publicacao, aberta), List.of(), List.of());
        ModeloDeFluxo hidrico = modelo("procOutorgaAguaSupSub");
        return AVALIAR.avaliar(bruto, ReconstruirCaminho.reconstruir(bruto, hidrico, Map.of()), hidrico,
                Optional.ofNullable(situacao));
    }

    @Test
    void ativa_na_verificacao_da_revisao_e_concluido_sem_bola_preta() {
        DesfechoDoCaminho d = ativa("taskVerificarRevisaoProcesso", "CONCLUIDO");
        assertThat(d.estado()).isEqualTo(DesfechoDoCaminho.Estado.CONCLUIDO);
        assertThat(d.bolaPreta()).isFalse();
        assertThat(d.rotulo()).contains("Concluído");
        assertThat(d.quando()).contains(T0.plusDays(4));
        assertThat(codigos(d)).containsExactly(CodigoDoAviso.CONCLUIDO_ANTES_DA_VERIFICACAO);
        // Sem situação, o aviso neutro continua; com situação que não confere, os dois.
        assertThat(codigos(ativa("taskVerificarRevisaoProcesso", null)))
                .containsExactly(CodigoDoAviso.CONCLUIDO_ANTES_DA_VERIFICACAO);
        assertThat(codigos(ativa("taskVerificarRevisaoProcesso", "EM_ANALISE")))
                .containsExactly(CodigoDoAviso.CONCLUIDO_ANTES_DA_VERIFICACAO, CodigoDoAviso.SITUACAO_NAO_CONFERE);
    }

    @Test
    void ativa_em_outra_etapa_esta_em_andamento_sem_rotulo() {
        DesfechoDoCaminho d = ativa("taskVerificarRevisaoProcesso".replace("VerificarRevisaoProcesso", "AnalisarRequerimento"),
                "EM_ANALISE");
        assertThat(d.estado()).isEqualTo(DesfechoDoCaminho.Estado.EM_ANDAMENTO);
        assertThat(d.rotulo()).isEmpty();
        assertThat(d.avisos()).isEmpty();
        assertThat(codigos(ativa("taskAnalisarRequerimento", "ARQUIVADO_TEMPORARIAMENTE")))
                .containsExactly(CodigoDoAviso.SITUACAO_ARQUIVADO_TEMPORARIAMENTE);
    }

    @Test
    void sem_instancia_tem_o_aviso_proprio() {
        DesfechoDoCaminho d = AVALIAR.avaliar(CaminhoBruto.semInstancia(), CaminhoReconstruido.vazio(),
                modelo("procOutorgaAguaSupSub"), Optional.of("CONCLUIDO"));
        assertThat(d.estado()).isEqualTo(DesfechoDoCaminho.Estado.SEM_INSTANCIA);
        assertThat(codigos(d)).containsExactly(CodigoDoAviso.SEM_INSTANCIA);
    }
}
