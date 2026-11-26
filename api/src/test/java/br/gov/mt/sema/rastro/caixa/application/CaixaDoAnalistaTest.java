package br.gov.mt.sema.rastro.caixa.application;

import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.A1;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.A2;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.A3;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.AGORA;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.ANALISE;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.CATALOGO;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.SAL;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.ativa;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.historia;
import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.posse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import br.gov.mt.sema.rastro.acesso.application.CadastroDeUsuarios;
import br.gov.mt.sema.rastro.acesso.application.ConsultarAnalistas;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria;
import br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.Caso;
import br.gov.mt.sema.rastro.caixa.application.AvisosDaCaixa.Aviso;
import br.gov.mt.sema.rastro.caixa.application.port.PosseDoAnalistaEmLote;
import br.gov.mt.sema.rastro.caixa.application.port.VistoDoAnalista;
import br.gov.mt.sema.rastro.caixa.domain.AnalistaParaLeitura;
import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.EstadoDePosse;
import br.gov.mt.sema.rastro.caixa.domain.FiltroDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.InstantaneoDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.ItemDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.LeituraDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.OrigemDoRetorno;
import br.gov.mt.sema.rastro.caixa.domain.PosseLida;
import br.gov.mt.sema.rastro.tramitacao.application.LimiteDeLeiturasDoCamunda;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import br.gov.mt.sema.rastro.tramitacao.domain.MotivosDaVolta;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Instantâneo, aviso de recebimento e visto da caixa (S11, bloco 5): a tabela do cenário (uma linha por requerimento), a
 * atualização repetida, o visto, o item que chega depois, o primeiro acesso, o analista sem CPF e a falha de leitura. Sem
 * Docker; dublês das portas.
 */
class CaixaDoAnalistaTest {

    private static final EstadoDePosse ANALISE_DO_PROCESSO = EstadoDePosse.comAnalista(MotivosDaVolta.ANALISE);

    /** Dublê do visto, igual em comportamento à implementação em memória. */
    private static final class VistoFalso implements VistoDoAnalista {
        final ConcurrentHashMap<ChaveDoResponsavel, Map<Long, EstadoDePosse>> mapa = new ConcurrentHashMap<>();

        @Override
        public Optional<Map<Long, EstadoDePosse>> ultimo(ChaveDoResponsavel analista) {
            return Optional.ofNullable(mapa.get(analista));
        }

        @Override
        public boolean gravarSeAusente(ChaveDoResponsavel analista, Map<Long, EstadoDePosse> estados) {
            return mapa.putIfAbsent(analista, Map.copyOf(estados)) == null;
        }

        @Override
        public void atualizar(ChaveDoResponsavel analista, UnaryOperator<Map<Long, EstadoDePosse>> regra) {
            mapa.compute(analista, (k, atual) -> Map.copyOf(regra.apply(atual == null ? Map.of() : atual)));
        }
    }

    /** Dublê da porta de leitura: devolve o que estiver na fonte, ou falha. */
    private static final class PortaFalsa implements PosseDoAnalistaEmLote {
        volatile List<PosseLida> fonte = List.of();
        volatile boolean falhar;
        final AtomicInteger leituras = new AtomicInteger();

        @Override
        public List<Long> candidatos(List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao, int maximo,
                Duration tempoLimite) {
            leituras.incrementAndGet();
            if (falhar) {
                throw new IllegalStateException("Oracle fora do ar");
            }
            return fonte.stream().map(PosseLida::idRequerimento).toList();
        }

        @Override
        public LeituraDaPosse ler(List<Long> ids, List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao,
                Duration tempoLimite) {
            return new LeituraDaPosse(fonte.stream().filter(p -> ids.contains(p.idRequerimento())).toList(), false);
        }
    }

    private static final class Ambiente {
        final PortaFalsa porta = new PortaFalsa();
        final VistoFalso visto = new VistoFalso();
        final GuardarInstantaneoDaCaixa guardar = new GuardarInstantaneoDaCaixa();
        final Map<Long, TramitacaoDoRequerimento> historias = new ConcurrentHashMap<>();
        final AtomicInteger historiasLidas = new AtomicInteger();
        final ChavesDosAnalistas chaves;
        final AtualizarCaixa atualizar;
        final AvisosDaCaixa avisos = new AvisosDaCaixa(guardar, visto);
        final CaixaDoAnalista caixa;
        final List<Runnable> pendentes = new ArrayList<>();
        final boolean emFilaManual;

        Ambiente() {
            this(false);
        }

        Ambiente(boolean emFilaManual) {
            this.emFilaManual = emFilaManual;
            CadastroDeUsuarios cadastro = mock(CadastroDeUsuarios.class);
            List<Usuario> usuarios = List.of(
                    new Usuario("analista.01", "Analista Exemplo 01", Perfil.ANALISTA, "00000000001", true),
                    new Usuario("analista.02", "Analista Exemplo 02", Perfil.ANALISTA, "00000000002", true),
                    new Usuario("analista.03", "Analista Exemplo 03", Perfil.ANALISTA, "00000000003", true),
                    new Usuario("analista.04", "Analista Exemplo 04", Perfil.ANALISTA, null, true),
                    new Usuario("analista.05", "Analista Exemplo 05", Perfil.ANALISTA, "00000000005", false),
                    new Usuario("gestor.01", "Gestor Exemplo 01", Perfil.OPERADOR_SIGILO, "00000000009", true));
            when(cadastro.listar()).thenReturn(usuarios);
            when(cadastro.buscarPorLogin(anyString())).thenAnswer(i -> usuarios.stream()
                    .filter(u -> u.login().equals(i.getArgument(0))).findFirst());
            chaves = new ChavesDosAnalistas(new ConsultarAnalistas(cadastro), SAL);
            Clock relogio = Clock.fixed(AGORA, ZoneId.of("America/Cuiaba"));
            ParametrosDaCaixa parametros = new ParametrosDaCaixa() {
                @Override
                public int atualizacaoMinutos() {
                    return 10;
                }

                @Override
                public int intervaloMinimoManualSegundos() {
                    return 60;
                }

                @Override
                public int limiteRequerimentos() {
                    return 3000;
                }

                @Override
                public int timeoutSegundos() {
                    return 60;
                }
            };
            LerOrigensDoRetorno origens = new LerOrigensDoRetorno(id -> {
                historiasLidas.incrementAndGet();
                return historias.get(id);
            }, System::nanoTime);
            atualizar = new AtualizarCaixa(new LerPosseEmLotes(porta, limite()), origens, chaves, CATALOGO, parametros,
                    visto, guardar, relogio, emFilaManual ? pendentes::add : Runnable::run, System::nanoTime);
            caixa = new CaixaDoAnalista(guardar, atualizar, avisos, CATALOGO, relogio);
        }

        void carregar(List<Caso> casos) {
            porta.fonte = CenarioDaCaixaEmMemoria.lidas(casos);
            casos.forEach(c -> {
                if (c.historia() != null) {
                    historias.put(c.id(), c.historia());
                }
            });
        }

        /** Põe no visto o que o cenário diz que constava antes, para cada analista. */
        void vistoAnterior(List<Caso> casos) {
            for (ChaveDoResponsavel dono : List.of(A1, A2, A3)) {
                visto.atualizar(dono, m -> {
                    Map<Long, EstadoDePosse> novo = new java.util.HashMap<>(m);
                    casos.stream().filter(c -> c.analista().equals(dono) && c.vistoAntes() != null)
                            .forEach(c -> novo.put(c.id(), c.vistoAntes()));
                    return novo;
                });
            }
        }

        void atualizarAgora() {
            atualizar.iniciarEmSegundoPlano();
            assertThat(atualizar.emAndamento()).isFalse();
        }
    }

    private static LimiteDeLeiturasDoCamunda limite() {
        return new LimiteDeLeiturasDoCamunda(new ParametrosDoRastro() {
            @Override
            public boolean mostrarNomeDoResponsavel() {
                return true;
            }

            @Override
            public int maximoDeNomesPorEquipe() {
                return 10;
            }

            @Override
            public int cacheDaPosicaoSegundos() {
                return 0;
            }

            @Override
            public int posicoesSimultaneas() {
                return 1;
            }

            @Override
            public int camundaTimeoutSegundos() {
                return 10;
            }

            @Override
            public int leiturasSimultaneasDoCamunda() {
                return 4;
            }
        });
    }

    static Stream<Caso> casos() {
        return CenarioDaCaixaEmMemoria.casos().stream();
    }

    private static Ambiente ambienteDoCenario() {
        Ambiente a = new Ambiente();
        List<Caso> casos = CenarioDaCaixaEmMemoria.casos();
        a.carregar(casos);
        a.vistoAnterior(casos);
        a.atualizarAgora();
        return a;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void cada_linha_do_cenario_tem_o_estado_o_aviso_e_a_origem_esperados(Caso caso) {
        Ambiente a = ambienteDoCenario();
        InstantaneoDaCaixa instantaneo = a.guardar.atual();
        assertThat(instantaneo.estado()).isEqualTo(InstantaneoDaCaixa.Estado.PRONTO);

        Optional<ItemDaCaixa> naCaixa = instantaneo.itensDe(caso.analista()).stream().filter(i -> i.id() == caso.id())
                .findFirst();
        assertThat(naCaixa.map(ItemDaCaixa::estado).orElse(EstadoDePosse.FORA)).as(caso.codigo()).isEqualTo(caso.esperado());

        Optional<Aviso> aviso = a.avisos.avisos(caso.analista()).stream().filter(v -> v.item().id() == caso.id()).findFirst();
        assertThat(aviso.isPresent()).as("aviso " + caso.codigo()).isEqualTo(caso.aviso());
        if (caso.aviso()) {
            assertThat(aviso.orElseThrow().origem()).as("origem " + caso.codigo()).isEqualTo(caso.origem());
        }
    }

    @Test
    void a1_recebe_seis_avisos_e_os_outros_analistas_nenhum() {
        Ambiente a = ambienteDoCenario();
        assertThat(a.avisos.avisos(A1)).extracting(v -> v.item().id())
                .containsExactly(15005L, 15006L, 15007L, 15008L, 15009L, 15017L);
        assertThat(a.avisos.avisos(A2)).isEmpty();
        assertThat(a.avisos.avisos(A3)).isEmpty();
        assertThat(a.historiasLidas.get()).as("só os seis avisados têm o histórico lido").isEqualTo(6);
    }

    @Test
    void duas_atualizacoes_seguidas_com_o_mesmo_item_avisam_uma_vez_e_nao_leem_o_historico_de_novo() {
        Ambiente a = ambienteDoCenario();
        assertThat(a.avisos.avisos(A1)).hasSize(6);
        a.atualizarAgora();
        a.atualizarAgora();
        assertThat(a.avisos.avisos(A1)).hasSize(6);
        assertThat(a.avisos.avisos(A1)).extracting(Aviso::origem).contains(OrigemDoRetorno.VOLTOU_DA_CORRECAO,
                OrigemDoRetorno.NOVO);
        assertThat(a.historiasLidas.get()).isEqualTo(6);
        a.avisos.marcarVisto(A1, a.avisos.idsAvisados(A1));
        a.atualizarAgora();
        assertThat(a.avisos.avisos(A1)).isEmpty();
    }

    @Test
    void abrir_a_lista_grava_o_visto_e_o_item_que_chega_depois_avisa_de_novo() {
        Ambiente a = ambienteDoCenario();
        Set<Long> vistos = a.avisos.idsAvisados(A1);
        assertThat(vistos).hasSize(6);
        a.avisos.marcarVisto(A1, vistos);
        assertThat(a.avisos.avisos(A1)).isEmpty();

        PosseLida nova = posse(18001, "Interessado Exemplo 01", null, ativa(ANALISE, 1, A1));
        a.porta.fonte = new ArrayList<>(a.porta.fonte);
        ((ArrayList<PosseLida>) a.porta.fonte).add(nova);
        a.historias.put(18001L, historia(MotivosDaVolta.ACEITE, MotivosDaVolta.ANALISE));
        a.atualizarAgora();
        List<Aviso> depois = a.avisos.avisos(A1);
        assertThat(depois).extracting(v -> v.item().id()).containsExactly(18001L);
        assertThat(depois.getFirst().origem()).isEqualTo(OrigemDoRetorno.NOVO);
    }

    @Test
    void duas_abas_o_visto_de_uma_nao_apaga_o_que_a_outra_ainda_nao_viu() {
        Ambiente a = ambienteDoCenario();
        Set<Long> abaUm = a.avisos.idsAvisados(A1);
        // chega um item entre o GET da aba um e o POST do visto
        a.porta.fonte = new ArrayList<>(a.porta.fonte);
        ((ArrayList<PosseLida>) a.porta.fonte).add(posse(18002, "Interessado Exemplo 03", null, ativa(ANALISE, 1, A1)));
        a.atualizarAgora();
        a.avisos.marcarVisto(A1, abaUm);
        assertThat(a.avisos.avisos(A1)).extracting(v -> v.item().id()).containsExactly(18002L);
        // a segunda aba (que tinha o mesmo conjunto) grava de novo: idempotente
        a.avisos.marcarVisto(A1, abaUm);
        assertThat(a.avisos.avisos(A1)).extracting(v -> v.item().id()).containsExactly(18002L);
        assertThat(a.avisos.avisos(A1).getFirst().origem()).isEqualTo(OrigemDoRetorno.MUDOU_DE_ETAPA);
    }

    @Test
    void primeiro_acesso_nao_avisa_nada_e_o_reinicio_vale_como_primeiro_acesso() {
        Ambiente a = new Ambiente();
        a.carregar(CenarioDaCaixaEmMemoria.casos());
        a.atualizarAgora();
        assertThat(a.visto.ultimo(A1)).as("ainda ninguém abriu").isEmpty();
        assertThat(a.historiasLidas.get()).as("sem visto não há avisados nem leitura de histórico").isZero();
        assertThat(a.avisos.avisos(A1)).isEmpty();
        assertThat(a.visto.ultimo(A1)).isPresent();
        assertThat(a.visto.ultimo(A1).orElseThrow()).containsKeys(15001L, 15005L, 15009L, 15004L);

        // reinício: o visto some, o instantâneo é o mesmo; de novo, sem aviso retroativo
        a.visto.mapa.clear();
        assertThat(a.avisos.avisos(A1)).isEmpty();
        assertThat(a.visto.ultimo(A1).orElseThrow()).containsKey(15005L);
    }

    @Test
    void sem_retrato_bom_nao_grava_visto_nem_avisa() {
        Ambiente a = new Ambiente();
        assertThat(a.avisos.avisos(A1)).isEmpty();
        a.avisos.marcarVisto(A1, Set.of(1L));
        assertThat(a.visto.ultimo(A1)).isEmpty();
    }

    @Test
    void analista_sem_cpf_nao_tem_chave_e_ve_a_caixa_vazia_com_a_marca_de_sem_vinculo() {
        Ambiente a = ambienteDoCenario();
        assertThat(a.chaves.chaveDoLogin("analista.04")).isEmpty();
        assertThat(a.chaves.chaveDoLogin("analista.05")).as("inativo").isEmpty();
        assertThat(a.chaves.chaveDoLogin("gestor.01")).as("gestor não tem caixa").isEmpty();
        assertThat(a.chaves.chaveDoLogin("analista.01")).contains(A1);
        assertThat(a.chaves.analistas()).hasSize(3);
        CaixaDoAnalista.Visao visao = a.caixa.abrir(Optional.empty(), Optional.empty(), "", FiltroDaCaixa.Ordem.MAIS_PARADO);
        assertThat(visao.semVinculo()).isTrue();
        assertThat(visao.linhas()).isEmpty();
        assertThat(visao.novos()).isZero();
        assertThat(visao.resumo().total()).isZero();
    }

    @Test
    void falha_de_leitura_mantem_o_ultimo_bom_e_sem_nenhum_bom_fica_indisponivel() {
        Ambiente a = new Ambiente();
        a.porta.falhar = true;
        a.atualizarAgora();
        assertThat(a.guardar.atual().estado()).isEqualTo(InstantaneoDaCaixa.Estado.INDISPONIVEL);

        a.porta.falhar = false;
        a.carregar(CenarioDaCaixaEmMemoria.casos());
        a.atualizarAgora();
        InstantaneoDaCaixa bom = a.guardar.atual();
        assertThat(bom.estado()).isEqualTo(InstantaneoDaCaixa.Estado.PRONTO);

        a.porta.falhar = true;
        a.atualizarAgora();
        InstantaneoDaCaixa depois = a.guardar.atual();
        assertThat(depois.estado()).isEqualTo(InstantaneoDaCaixa.Estado.DESATUALIZADO);
        assertThat(depois.itens()).isEqualTo(bom.itens());
        assertThat(depois.geradoEm()).isEqualTo(bom.geradoEm());
        assertThat(depois.falhouEm()).isPresent();
    }

    @Test
    void falha_ao_ler_o_historico_nao_perde_a_atualizacao_e_o_aviso_diz_mudou_de_etapa() {
        Ambiente a = new Ambiente();
        List<Caso> casos = CenarioDaCaixaEmMemoria.casos();
        a.carregar(casos);
        a.vistoAnterior(casos);
        a.historias.clear(); // a leitura devolve null: origem desconhecida
        a.atualizarAgora();
        assertThat(a.guardar.atual().estado()).isEqualTo(InstantaneoDaCaixa.Estado.PRONTO);
        assertThat(a.avisos.avisos(A1)).hasSize(6).allSatisfy(v -> assertThat(v.origem())
                .isEqualTo(OrigemDoRetorno.MUDOU_DE_ETAPA));
    }

    @Test
    void a_primeira_requisicao_dispara_a_leitura_em_segundo_plano_uma_so_vez() {
        Ambiente a = new Ambiente(true);
        a.carregar(CenarioDaCaixaEmMemoria.casos());
        CaixaDoAnalista.Visao primeira = a.caixa.abrir(Optional.of(A1), Optional.empty(), "", FiltroDaCaixa.Ordem.MAIS_PARADO);
        assertThat(primeira.estado()).isEqualTo(InstantaneoDaCaixa.Estado.PREPARANDO);
        assertThat(primeira.linhas()).isEmpty();
        a.caixa.abrir(Optional.of(A1), Optional.empty(), "", FiltroDaCaixa.Ordem.MAIS_PARADO);
        assertThat(a.pendentes).hasSize(1);
        assertThat(a.porta.leituras.get()).as("nada lido dentro da requisição").isZero();
        a.pendentes.getFirst().run();
        assertThat(a.guardar.atual().estado()).isEqualTo(InstantaneoDaCaixa.Estado.PRONTO);
        assertThat(a.atualizar.ultimoInicio()).isPresent();
    }

    @Test
    void a_visao_traz_resumo_dias_marca_novo_ordem_e_busca() {
        Ambiente a = ambienteDoCenario();
        CaixaDoAnalista.Visao geral = a.caixa.abrir(Optional.of(A1), Optional.empty(), "", FiltroDaCaixa.Ordem.MAIS_PARADO);
        assertThat(geral.semVinculo()).isFalse();
        assertThat(geral.resumo().total()).isEqualTo(10);
        assertThat(geral.resumo().comInteressado()).isEqualTo(1);
        assertThat(geral.resumo().porEtapa()).extracting(e -> e.quantidade()).containsExactly(7, 2);
        assertThat(geral.novos()).isEqualTo(6);
        assertThat(geral.linhas().getFirst().item().id()).as("mais parado primeiro: 400 dias").isEqualTo(15014L);
        assertThat(geral.linhas().getFirst().diasNaEtapa()).contains(400L);
        assertThat(geral.linhas().stream().filter(CaixaDoAnalista.Linha::novo).map(l -> l.item().id()))
                .containsExactlyInAnyOrder(15005L, 15006L, 15007L, 15008L, 15009L, 15017L);

        CaixaDoAnalista.Visao recentes = a.caixa.abrir(Optional.of(A1), Optional.empty(), "", FiltroDaCaixa.Ordem.MAIS_RECENTE);
        assertThat(recentes.linhas().getFirst().item().id()).isEqualTo(15009L);

        CaixaDoAnalista.Visao correcao = a.caixa.abrir(Optional.of(A1), Optional.of("correcao_do_requerimento"), "",
                FiltroDaCaixa.Ordem.MAIS_PARADO);
        assertThat(correcao.linhas()).extracting(l -> l.item().id()).containsExactly(15004L);
        assertThat(correcao.resumo().total()).as("o resumo é da caixa inteira, não do filtro").isEqualTo(10);

        CaixaDoAnalista.Visao busca = a.caixa.abrir(Optional.of(A1), Optional.empty(), "cooperativa",
                FiltroDaCaixa.Ordem.MAIS_PARADO);
        assertThat(busca.linhas()).extracting(l -> l.item().id()).containsExactly(15004L, 15008L);
    }

    @Test
    void a_caixa_do_a3_nao_mostra_nada_do_a1_e_a_do_a2_tem_a_correcao_dele() {
        Ambiente a = ambienteDoCenario();
        assertThat(a.caixa.abrir(Optional.of(A3), Optional.empty(), "", FiltroDaCaixa.Ordem.MAIS_PARADO).linhas())
                .extracting(l -> l.item().id()).containsExactly(15016L);
        CaixaDoAnalista.Visao a2 = a.caixa.abrir(Optional.of(A2), Optional.empty(), "", FiltroDaCaixa.Ordem.MAIS_PARADO);
        assertThat(a2.linhas()).extracting(l -> l.item().id()).containsExactlyInAnyOrder(15002L, 15013L);
        assertThat(a2.resumo().comInteressado()).isEqualTo(1);
    }

    @Test
    void nenhum_cpf_nem_o_identificador_aparecem_no_instantaneo_nem_nos_avisos() {
        Ambiente a = ambienteDoCenario();
        String tudo = a.guardar.atual().toString() + a.avisos.avisos(A1) + a.chaves.analistas();
        for (String cpf : List.of("00000000001", "00000000002", "00000000003")) {
            assertThat(tudo).doesNotContain(cpf);
        }
        assertThat(ANALISE_DO_PROCESSO.toString()).doesNotContain("0000");
    }
}
