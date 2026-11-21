package br.gov.mt.sema.rastro.tramitacao.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDoConfrontoDeTeste;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Confronto situação × fluxo (S7, bloco 5) com os catálogos reais ({@code rastro/etapas/*.yml} e
 * {@code rastro/confronto.yaml}). O confronto só acrescenta avisos: a posição sai idêntica (o mesmo objeto).
 */
class ReconciliarTest {

    private static final String HIDRICO = "procOutorgaAguaSupSub";
    private static final String LAC = "procLicenciamentoLac";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 9, 9, 30);
    private static final CatalogoDeEtapas ETAPAS = CatalogoDeEtapasDeTeste.padrao();
    private static final Reconciliar RECONCILIAR = new Reconciliar(CatalogoDoConfrontoDeTeste.padrao());

    private static PosicaoAtual ativaEm(String chave, String idTecnico, Set<MarcaDaPosicao> marcas) {
        ModeloDeFluxo modelo = ETAPAS.modelo(chave).orElseThrow();
        Etapa etapa = ((ResolucaoDeEtapa.NaEtapa) modelo.resolver(idTecnico, Set.of())).etapa();
        ElementoDaPosicao ponto = new ElementoDaPosicao(Optional.of(etapa), idTecnico, T0, true, List.of(),
                Optional.empty(), Optional.empty());
        return new PosicaoAtual(EstadoDaPosicao.ATIVO, Optional.of(chave), Optional.of(modelo.rotulo()), Optional.of(ponto),
                List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), marcas, List.of(),
                Optional.empty());
    }

    private static PosicaoAtual encerradaEm(String chave, String idDoFinal) {
        ModeloDeFluxo modelo = ETAPAS.modelo(chave).orElseThrow();
        FinalDoFluxo fim = ((ResolucaoDeEtapa.Encerrado) modelo.resolver(idDoFinal, Set.of())).fim();
        return new PosicaoAtual(EstadoDaPosicao.ENCERRADO, Optional.of(chave), Optional.of(modelo.rotulo()),
                Optional.empty(), List.of(), Optional.empty(), Optional.of(fim), Optional.of(T0), Optional.empty(), Set.of(),
                List.of(), Optional.empty());
    }

    private static List<CodigoDoAviso> codigos(PosicaoConfrontada p) {
        return p.avisos().stream().map(Aviso::codigo).toList();
    }

    @Test
    void fluxo_encerrado_por_taxa_sem_atualizar_a_situacao() {
        PosicaoAtual h18 = encerradaEm(HIDRICO, "Event_0h1oige");
        PosicaoConfrontada p = RECONCILIAR.reconciliar(h18, Optional.of("AGUARDANDO_PAGAMENTO_DE_TAXAS"));
        assertThat(p.posicao()).isSameAs(h18);
        assertThat(p.avisos()).singleElement().satisfies(a -> {
            assertThat(a.codigo()).isEqualTo(CodigoDoAviso.FLUXO_ENCERRADO_SEM_STATUS);
            assertThat(a.gravidade()).isEqualTo(Aviso.Gravidade.ATENCAO);
            assertThat(a.texto()).contains("taxa não paga").contains("aguardando pagamento");
        });
        // A posição mostrada continua o final do fluxo, e a situação do SIGA não é trocada.
        assertThat(p.posicao().fim()).map(FinalDoFluxo::rotulo).contains("Encerrado por taxa não paga no prazo");
        assertThat(p.situacaoDoLicenciamento()).contains("AGUARDANDO_PAGAMENTO_DE_TAXAS");
        // Quando o SIGA corrigir o bug de origem, o aviso some sozinho.
        assertThat(RECONCILIAR.reconciliar(h18, Optional.of("CANCELADO_POR_FALTA_DE_PAGAMENTO")).avisos()).isEmpty();
    }

    @Test
    void concluido_antes_da_verificacao_e_informativo() {
        PosicaoConfrontada p = RECONCILIAR.reconciliar(ativaEm(HIDRICO, "taskVerificarRevisaoProcesso", Set.of()),
                Optional.of("CONCLUIDO"));
        assertThat(p.avisos()).singleElement().satisfies(a -> {
            assertThat(a.codigo()).isEqualTo(CodigoDoAviso.CONCLUIDO_ANTES_DA_VERIFICACAO);
            assertThat(a.gravidade()).isEqualTo(Aviso.Gravidade.INFO);
        });
    }

    @Test
    void expirado_na_correcao_do_lac_vale_o_final() {
        PosicaoAtual l07 = encerradaEm(LAC, "evtEncerrarProcessoPrazoCorrecaoExpirado");
        PosicaoConfrontada p = RECONCILIAR.reconciliar(l07, Optional.of("CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO"));
        assertThat(codigos(p)).containsExactly(CodigoDoAviso.EXPIRADO_NA_CORRECAO_LAC);
        assertThat(p.posicao().fim()).map(FinalDoFluxo::rotulo).contains("Expirado na correção");
    }

    @ParameterizedTest(name = "{0} em {1}")
    @CsvSource({
            "procOutorgaAguaSupSub, taskConferirDocumentosApresentados, EM_ANALISE",
            "procOutorgaAguaSupSub, taskAnalisarRequerimento, AGUARDANDO_PAGAMENTO_DE_TAXAS",
            "procOutorgaAguaSupSub, taskVerificarRevisaoProcesso, EM_ANALISE",
            "procLicenciamentoLac, taskEnviarTermoAdesao, EM_CONFERENCIA"})
    void situacao_incompativel_tem_texto_neutro_com_o_valor(String chave, String idTecnico, String situacao) {
        PosicaoAtual posicao = ativaEm(chave, idTecnico, Set.of());
        PosicaoConfrontada p = RECONCILIAR.reconciliar(posicao, Optional.of(situacao));
        assertThat(p.posicao()).isSameAs(posicao);
        assertThat(p.avisos()).singleElement().satisfies(a -> {
            assertThat(a.codigo()).isEqualTo(CodigoDoAviso.SITUACAO_NAO_CONFERE);
            assertThat(a.texto()).isEqualTo("A situação do SIGA ('" + situacao
                    + "') não corresponde exatamente à etapa do fluxo. Vale a etapa do fluxo.");
        });
    }

    @ParameterizedTest(name = "{2} em {1}")
    @CsvSource({
            "procOutorgaAguaSupSub, taskEmitirTaxaSefaz, AGUARDANDO_EMISSAO_DE_TAXAS",
            "procOutorgaAguaSupSub, taskEmitirTaxaSefazCorrecao, AGUARDANDO_EMISSAO_DE_TAXAS",
            "procOutorgaAguaSupSub, taskAnalisarRequerimento, EM_ANALISE",
            "procLicenciamentoLac, taskEnviarTermoAdesao, AGUARDANDO_ASSINATURA_LAC"})
    void situacao_compativel_nao_gera_aviso(String chave, String idTecnico, String situacao) {
        assertThat(RECONCILIAR.reconciliar(ativaEm(chave, idTecnico, Set.of()), Optional.of(situacao)).avisos()).isEmpty();
    }

    @Test
    void marcas_da_leitura_viram_avisos_de_mesmo_nome() {
        PosicaoConfrontada p = RECONCILIAR.reconciliar(ativaEm(HIDRICO, "taskConferirDocumentosApresentados",
                Set.of(MarcaDaPosicao.VARIAS_INSTANCIAS, MarcaDaPosicao.EM_SUPORTE, MarcaDaPosicao.ETAPA_NAO_MAPEADA)),
                Optional.of("EM_CONFERENCIA"));
        assertThat(codigos(p)).containsExactly(CodigoDoAviso.VARIAS_INSTANCIAS, CodigoDoAviso.EM_SUPORTE,
                CodigoDoAviso.ETAPA_NAO_MAPEADA);
        assertThat(p.avisos()).filteredOn(a -> a.codigo() == CodigoDoAviso.EM_SUPORTE).singleElement()
                .satisfies(a -> assertThat(a.texto()).isEqualTo("Em atendimento de suporte técnico."));
    }

    @Test
    void sem_instancia_nao_suportado_e_indisponivel_nao_sao_confrontados() {
        ModeloDeFluxo hidrico = ETAPAS.modelo(HIDRICO).orElseThrow();
        assertThat(codigos(RECONCILIAR.reconciliar(PosicaoAtual.semInstancia(hidrico), Optional.of("CONCLUIDO"))))
                .containsExactly(CodigoDoAviso.SEM_INSTANCIA);
        assertThat(RECONCILIAR.reconciliar(PosicaoAtual.naoSuportado(), Optional.of("EM_ANALISE")).avisos()).isEmpty();
        assertThat(RECONCILIAR.reconciliar(PosicaoAtual.indisponivel(hidrico), Optional.of("EM_CADASTRO")).avisos())
                .isEmpty();
    }

    @Test
    void sem_situacao_ou_principal_nao_mapeada_nao_confronta() {
        assertThat(RECONCILIAR.reconciliar(ativaEm(HIDRICO, "taskAnalisarRequerimento", Set.of()), Optional.empty())
                .avisos()).isEmpty();
        assertThat(RECONCILIAR.reconciliar(ativaEm(HIDRICO, "taskAnalisarRequerimento", Set.of()), Optional.of("  "))
                .situacaoDoLicenciamento()).isEmpty();
        ElementoDaPosicao naoMapeado = new ElementoDaPosicao(Optional.empty(), "taskNova", T0, false, List.of(),
                Optional.empty(), Optional.empty());
        PosicaoAtual posicao = new PosicaoAtual(EstadoDaPosicao.ATIVO, Optional.of(HIDRICO), Optional.of("Hídrico"),
                Optional.of(naoMapeado), List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Set.of(MarcaDaPosicao.ETAPA_NAO_MAPEADA), List.of("taskNova"), Optional.empty());
        assertThat(codigos(RECONCILIAR.reconciliar(posicao, Optional.of("EM_CADASTRO"))))
                .containsExactly(CodigoDoAviso.ETAPA_NAO_MAPEADA);
    }

    @Test
    void finais_compativeis_nao_geram_aviso() {
        assertThat(RECONCILIAR.reconciliar(encerradaEm(HIDRICO, "Event_1wiuc48"), Optional.of("CONCLUIDO")).avisos())
                .isEmpty();
        assertThat(RECONCILIAR.reconciliar(encerradaEm(LAC, "Event_0x7hz1p"), Optional.of("ARQUIVADO_DEFINITIVAMENTE"))
                .avisos()).isEmpty();
        assertThat(codigos(RECONCILIAR.reconciliar(encerradaEm(HIDRICO, "Event_1wiuc48"), Optional.of("EM_ANALISE"))))
                .containsExactly(CodigoDoAviso.SITUACAO_NAO_CONFERE);
    }
}
