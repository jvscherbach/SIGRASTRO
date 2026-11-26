package br.gov.mt.sema.rastro.caixa.domain;

import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.A1;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.A2;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.A3;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.AGORA;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.ANALISE;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.CATALOGO;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.CORRECAO;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.DEFINICAO;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.MODELO;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.REVISAO;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.ativa;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.haDias;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.historia;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.posse;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria;
import br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.Caso;
import br.gov.mt.sema.rastro.tramitacao.domain.MotivosDaVolta;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Estado de posse, origem do retorno, aviso, resumo, filtro e dias na etapa (S11, bloco 5). Sem Docker. */
class RegrasDaCaixaTest {

    private static final EstadoDePosse ANALISE_DO_PROCESSO = EstadoDePosse.comAnalista(MotivosDaVolta.ANALISE);
    private static final EstadoDePosse REVISAO_DO_PARECER = EstadoDePosse.comAnalista(MotivosDaVolta.REVISAO);

    static Stream<Caso> casos() {
        return CenarioDaCaixaEmMemoria.casos().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void o_estado_de_posse_de_cada_linha_do_cenario(Caso caso) {
        Optional<ItemDaCaixa> decidido = caso.lida()
                .flatMap(l -> DecidirPosse.decidir(l, caso.analista(), DEFINICAO, CATALOGO));
        assertThat(decidido.map(ItemDaCaixa::estado).orElse(EstadoDePosse.FORA)).as(caso.codigo()).isEqualTo(caso.esperado());
        assertThat(decidido.flatMap(i -> i.diasNaEtapa(AGORA))).as("dias " + caso.codigo()).isEqualTo(caso.dias());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void o_aviso_de_cada_linha_depende_do_estado_atual_e_do_visto(Caso caso) {
        Optional<ItemDaCaixa> item = CenarioDaCaixaEmMemoria.itemEsperado(caso);
        Map<Long, EstadoDePosse> visto = caso.vistoAntes() == null ? Map.of() : Map.of(caso.id(), caso.vistoAntes());
        boolean avisa = item.map(i -> RegrasDoAviso.chegou(i, visto)).orElse(false);
        assertThat(avisa).as(caso.codigo()).isEqualTo(caso.aviso());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void a_origem_de_cada_retorno_vem_do_historico(Caso caso) {
        if (caso.origem() == null) {
            return;
        }
        assertThat(OrigemDoRetorno.de(caso.historia())).as(caso.codigo()).isEqualTo(caso.origem());
    }

    @Test
    void cada_analista_so_ve_o_que_e_dele_e_a_correcao_continua_sendo_do_autor_da_ultima_analise() {
        List<PosseLida> lidas = CenarioDaCaixaEmMemoria.lidas(CenarioDaCaixaEmMemoria.casos());
        Map<ChaveDoResponsavel, List<ItemDaCaixa>> porAnalista = DecidirPosse.agrupar(lidas, DEFINICAO, CATALOGO);
        assertThat(porAnalista.get(A1)).extracting(ItemDaCaixa::id)
                .containsExactly(15001L, 15003L, 15004L, 15005L, 15006L, 15007L, 15008L, 15009L, 15014L, 15017L);
        assertThat(porAnalista.get(A2)).extracting(ItemDaCaixa::id).containsExactly(15002L, 15013L);
        assertThat(porAnalista.get(A3)).extracting(ItemDaCaixa::id).containsExactly(15016L);
        assertThat(porAnalista.get(A1).stream().filter(i -> !i.estado().comAnalista()).map(ItemDaCaixa::id))
                .containsExactly(15004L);
    }

    @Test
    void tarefa_de_grupo_modelo_desconhecido_e_etapa_fora_do_catalogo_nao_entram() {
        PosseLida deGrupo = posse(1, "Interessado Exemplo 01", null, ativa(ANALISE, 3, null));
        assertThat(DecidirPosse.agrupar(List.of(deGrupo), DEFINICAO, CATALOGO)).isEmpty();
        PosseLida outroModelo = new PosseLida(2, Optional.empty(), "modeloQueNaoExiste",
                List.of(ativa(ANALISE, 3, A1)), Optional.empty(), Optional.empty(), Optional.empty());
        assertThat(DecidirPosse.decidir(outroModelo, A1, DEFINICAO, CATALOGO)).isEmpty();
        PosseLida atividadeEstranha = posse(3, "Interessado Exemplo 01", null, ativa("taskQueNaoExiste", 3, A1));
        assertThat(DecidirPosse.decidir(atividadeEstranha, A1, DEFINICAO, CATALOGO)).isEmpty();
    }

    @Test
    void ultima_analise_de_outro_analista_ou_de_nao_analista_nao_da_posse_na_correcao() {
        var deA2 = new PosseLida.UltimaTarefaDeAnalise(ANALISE, Optional.of(A2), haDias(5));
        PosseLida correcao = posse(4, "Interessado Exemplo 01", deA2, ativa(CORRECAO, 2, null));
        assertThat(DecidirPosse.decidir(correcao, A1, DEFINICAO, CATALOGO)).isEmpty();
        assertThat(DecidirPosse.decidir(correcao, A2, DEFINICAO, CATALOGO)).isPresent();
        var deQuemNaoEAnalista = new PosseLida.UltimaTarefaDeAnalise(ANALISE, Optional.empty(), haDias(5));
        assertThat(DecidirPosse.agrupar(List.of(posse(5, "Interessado Exemplo 01", deQuemNaoEAnalista,
                ativa(CORRECAO, 2, null))), DEFINICAO, CATALOGO)).isEmpty();
    }

    @Test
    void sem_inicio_da_tarefa_o_item_fica_sem_informacao_de_dias() {
        PosseLida semInicio = new PosseLida(6, Optional.of("6/2028"), MODELO,
                List.of(new PosseLida.TarefaAtivaDaPosse(ANALISE, null, Optional.of(A1))), Optional.empty(),
                Optional.of("Interessado Exemplo 01"), Optional.empty());
        ItemDaCaixa item = DecidirPosse.decidir(semInicio, A1, DEFINICAO, CATALOGO).orElseThrow();
        assertThat(item.inicioDaEtapa()).isEmpty();
        assertThat(item.diasNaEtapa(AGORA)).isEmpty();
        Instant antesDoInicio = AGORA.minusSeconds(86400L * 30);
        assertThat(DecidirPosse.decidir(posse(7, "Interessado Exemplo 01", null, ativa(ANALISE, 1, A1)), A1, DEFINICAO,
                CATALOGO).orElseThrow().diasNaEtapa(antesDoInicio)).contains(0L);
    }

    @Test
    void origem_sem_historico_ou_sem_passagem_de_posse_e_mudou_de_etapa_e_nunca_inventa() {
        assertThat(OrigemDoRetorno.de(null)).isEqualTo(OrigemDoRetorno.MUDOU_DE_ETAPA);
        assertThat(OrigemDoRetorno.de(TramitacaoDoRequerimento.indisponivel(Optional.empty(), Optional.empty())))
                .isEqualTo(OrigemDoRetorno.MUDOU_DE_ETAPA);
        assertThat(OrigemDoRetorno.de(historia(MotivosDaVolta.VALIDACAO))).isEqualTo(OrigemDoRetorno.MUDOU_DE_ETAPA);
        // da análise para a revisão (dentro da posse): não é "voltou" de lugar nenhum
        assertThat(OrigemDoRetorno.de(historia(MotivosDaVolta.ANALISE, MotivosDaVolta.REVISAO)))
                .isEqualTo(OrigemDoRetorno.MUDOU_DE_ETAPA);
        // da validação direto para a análise de 1º ciclo (sem ciclo anterior): ainda assim diz de onde veio
        assertThat(OrigemDoRetorno.de(historia(MotivosDaVolta.VALIDACAO, MotivosDaVolta.ANALISE)))
                .isEqualTo(OrigemDoRetorno.VOLTOU_DA_VALIDACAO);
        // a análise é a primeira passagem do histórico: nada antes
        assertThat(OrigemDoRetorno.de(historia(MotivosDaVolta.ANALISE))).isEqualTo(OrigemDoRetorno.NOVO);
        assertThat(OrigemDoRetorno.NOVO.rotulo()).isEqualTo("Novo na sua caixa");
        assertThat(OrigemDoRetorno.MUDOU_DE_ETAPA.rotulo()).isEqualTo("Mudou de etapa");
    }

    @Test
    void abrir_a_lista_grava_o_visto_so_do_que_foi_visto_e_o_que_saiu_da_caixa_avisa_ao_voltar() {
        ItemDaCaixa um = item(1, ANALISE_DO_PROCESSO);
        ItemDaCaixa dois = item(2, ANALISE_DO_PROCESSO);
        ItemDaCaixa tres = item(3, REVISAO_DO_PARECER);
        Map<Long, EstadoDePosse> visto = Map.of(3L, ANALISE_DO_PROCESSO);
        assertThat(RegrasDoAviso.avisados(List.of(um, dois, tres), visto)).containsExactly(um, dois);
        // a aba viu só o 1: o 2 continua avisando; o 3 (dentro da posse) fica com o estado atual
        Map<Long, EstadoDePosse> depois = RegrasDoAviso.aposAbrirALista(List.of(um, dois, tres), visto, java.util.Set.of(1L));
        assertThat(depois).containsEntry(1L, ANALISE_DO_PROCESSO).containsEntry(3L, REVISAO_DO_PARECER)
                .doesNotContainKey(2L);
        assertThat(RegrasDoAviso.avisados(List.of(um, dois, tres), depois)).containsExactly(dois);
        // o 1 sai da caixa (some do instantâneo) e some do visto: ao voltar, avisa
        Map<Long, EstadoDePosse> semOUm = RegrasDoAviso.aposAbrirALista(List.of(dois, tres), depois, java.util.Set.of(2L));
        assertThat(semOUm).doesNotContainKey(1L);
        assertThat(RegrasDoAviso.avisados(List.of(um), semOUm)).containsExactly(um);
    }

    @Test
    void resumo_por_etapa_com_zeradas_e_com_o_interessado() {
        List<ItemDaCaixa> itens = List.of(item(1, ANALISE_DO_PROCESSO), item(2, ANALISE_DO_PROCESSO),
                item(3, EstadoDePosse.COM_INTERESSADO));
        ResumoDaCaixa resumo = ResumoDaCaixa.de(itens, CATALOGO);
        assertThat(resumo.total()).isEqualTo(3);
        assertThat(resumo.comInteressado()).isEqualTo(1);
        assertThat(resumo.porEtapa()).extracting(ResumoDaCaixa.EtapaDoResumo::id, ResumoDaCaixa.EtapaDoResumo::quantidade)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(MotivosDaVolta.ANALISE, 2),
                        org.assertj.core.groups.Tuple.tuple(MotivosDaVolta.REVISAO, 0));
        assertThat(resumo.porEtapa().get(0).rotulo()).isEqualTo("Análise do processo");
        assertThat(ResumoDaCaixa.de(List.of(), CATALOGO).total()).isZero();
    }

    @Test
    void filtro_ordem_e_busca_sem_acento_nem_caixa() {
        ItemDaCaixa antigo = com(1, "Interessado Exemplo LTDA", 20, ANALISE_DO_PROCESSO);
        ItemDaCaixa recente = com(2, "Cooperativa Exemplo 04", 2, REVISAO_DO_PARECER);
        ItemDaCaixa semInicio = new ItemDaCaixa(3, Optional.of("3/2028"), Optional.of("Associação Exemplo"),
                Optional.empty(), ANALISE_DO_PROCESSO, Optional.empty());
        ItemDaCaixa correcao = new ItemDaCaixa(4, Optional.of("4/2028"), Optional.of("Interessado Exemplo 01"),
                Optional.empty(), EstadoDePosse.COM_INTERESSADO, Optional.of(haDias(12)));
        List<ItemDaCaixa> todos = List.of(recente, semInicio, antigo, correcao);
        assertThat(FiltroDaCaixa.aplicar(todos, Optional.empty(), "", FiltroDaCaixa.Ordem.MAIS_PARADO))
                .extracting(ItemDaCaixa::id).containsExactly(1L, 4L, 2L, 3L);
        assertThat(FiltroDaCaixa.aplicar(todos, Optional.empty(), null, FiltroDaCaixa.Ordem.MAIS_RECENTE))
                .extracting(ItemDaCaixa::id).containsExactly(2L, 4L, 1L, 3L);
        assertThat(FiltroDaCaixa.aplicar(todos, Optional.of(MotivosDaVolta.ANALISE), "", FiltroDaCaixa.Ordem.MAIS_PARADO))
                .extracting(ItemDaCaixa::id).containsExactly(1L, 3L);
        assertThat(FiltroDaCaixa.aplicar(todos, Optional.of(DefinicaoDaPosse.ETAPA_CORRECAO), "", FiltroDaCaixa.Ordem.MAIS_PARADO))
                .extracting(ItemDaCaixa::id).containsExactly(4L);
        assertThat(FiltroDaCaixa.aplicar(todos, Optional.empty(), "ASSOCIACAO", FiltroDaCaixa.Ordem.MAIS_PARADO))
                .extracting(ItemDaCaixa::id).containsExactly(3L);
        assertThat(FiltroDaCaixa.aplicar(todos, Optional.empty(), " 2/2028 ", FiltroDaCaixa.Ordem.MAIS_PARADO))
                .extracting(ItemDaCaixa::id).containsExactly(2L);
        assertThat(FiltroDaCaixa.aplicar(todos, Optional.empty(), "nada disso", FiltroDaCaixa.Ordem.MAIS_PARADO)).isEmpty();
    }

    @Test
    void o_instantaneo_mantem_o_ultimo_bom_na_falha_e_nao_tem_onde_guardar_cpf() {
        assertThat(InstantaneoDaCaixa.PREPARANDO.temDadosBons()).isFalse();
        assertThat(InstantaneoDaCaixa.PREPARANDO.aposFalha(AGORA).estado()).isEqualTo(InstantaneoDaCaixa.Estado.INDISPONIVEL);
        InstantaneoDaCaixa bom = InstantaneoDaCaixa.pronto(AGORA, true, Map.of(A1, List.of(item(1, ANALISE_DO_PROCESSO))),
                Map.of());
        InstantaneoDaCaixa depois = bom.aposFalha(AGORA.plusSeconds(60));
        assertThat(depois.estado()).isEqualTo(InstantaneoDaCaixa.Estado.DESATUALIZADO);
        assertThat(depois.geradoEm()).isEqualTo(bom.geradoEm());
        assertThat(depois.itensDe(A1)).hasSize(1);
        assertThat(depois.aposFalha(AGORA.plusSeconds(120)).estado()).isEqualTo(InstantaneoDaCaixa.Estado.DESATUALIZADO);
        assertThat(depois.itensDe(A2)).isEmpty();
        assertThat(java.util.Arrays.stream(InstantaneoDaCaixa.class.getRecordComponents()).map(c -> c.getName().toLowerCase()))
                .noneMatch(n -> n.contains("cpf"));
    }

    @Test
    void estado_de_posse_so_tem_etapa_quando_esta_com_o_analista() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new EstadoDePosse(EstadoDePosse.Tipo.FORA, Optional.of("x")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new EstadoDePosse(EstadoDePosse.Tipo.COM_ANALISTA, Optional.empty()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ItemDaCaixa(1, Optional.empty(), Optional.empty(), Optional.empty(), EstadoDePosse.FORA,
                        Optional.empty()));
        assertThat(A3).isNotEqualTo(A1);
    }

    private static ItemDaCaixa item(long id, EstadoDePosse estado) {
        return new ItemDaCaixa(id, Optional.of(id + "/2028"), Optional.of("Interessado Exemplo 01"), Optional.empty(), estado,
                Optional.of(haDias(5)));
    }

    private static ItemDaCaixa com(long id, String interessado, long dias, EstadoDePosse estado) {
        return new ItemDaCaixa(id, Optional.of(id + "/2028"), Optional.of(interessado), Optional.empty(), estado,
                Optional.of(haDias(dias)));
    }
}
