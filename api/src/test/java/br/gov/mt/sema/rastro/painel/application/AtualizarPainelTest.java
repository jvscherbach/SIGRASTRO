package br.gov.mt.sema.rastro.painel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import br.gov.mt.sema.rastro.painel.application.port.ResumoDoAnoNoSiga;
import br.gov.mt.sema.rastro.painel.application.port.TarefasAtivasEmLote;
import br.gov.mt.sema.rastro.painel.domain.AnoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.BaseDoAno;
import br.gov.mt.sema.rastro.painel.domain.ContagemDoMes;
import br.gov.mt.sema.rastro.painel.domain.EstadoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.InstantaneoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.LeituraDaSerie;
import br.gov.mt.sema.rastro.painel.domain.LeituraDasTarefas;
import br.gov.mt.sema.rastro.painel.domain.RequerimentoDaBase;
import br.gov.mt.sema.rastro.painel.domain.ResponsavelDaTarefa;
import br.gov.mt.sema.rastro.painel.domain.TarefaAtiva;
import br.gov.mt.sema.rastro.tramitacao.application.LimiteDeLeiturasDoCamunda;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeGrupos;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Instantâneo em memória e atualização (S10, bloco 4), sem Docker, com dublês das portas. */
class AtualizarPainelTest {

    private static final CatalogoDeEtapas CATALOGO = CatalogoDeEtapasDeTeste.padrao();
    private static final CatalogoDeGrupos GRUPOS = id -> Optional.empty();
    private static final String INSTANCIA = CATALOGO.modelos().get(0).instancias().get(0);
    private static final String MODELO = CATALOGO.modelos().get(0).chave();

    /** Relógio de teste que anda quando mandado. Fuso UTC de propósito: o ano do painel é o de Cuiabá. */
    private static final class RelogioMutavel extends Clock {
        volatile Instant agora;

        RelogioMutavel(Instant inicio) {
            this.agora = inicio;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private static final class Parametros implements ParametrosDoPainel {
        volatile int ano = 0;
        volatile int limite = 3000;

        @Override
        public int ano() {
            return ano;
        }

        @Override
        public int atualizacaoMinutos() {
            return 30;
        }

        @Override
        public int limiteRequerimentos() {
            return limite;
        }

        @Override
        public int timeoutSegundos() {
            return 60;
        }

        @Override
        public int intervaloMinimoManualSegundos() {
            return 120;
        }
    }

    /** Dublê das duas portas: conta as chamadas, pode falhar e pode esperar. */
    private static final class Portas implements ResumoDoAnoNoSiga, TarefasAtivasEmLote {
        final AtomicInteger series = new AtomicInteger();
        final AtomicInteger bases = new AtomicInteger();
        final AtomicInteger lotes = new AtomicInteger();
        final List<Integer> anos = new ArrayList<>();
        volatile boolean falhar;
        volatile int quantosRequerimentos = 3;
        volatile CountDownLatch segurar;
        volatile CountDownLatch entrou = new CountDownLatch(1);

        int total() {
            return series.get() + bases.get() + lotes.get();
        }

        @Override
        public LeituraDaSerie lerSerie(AnoDoPainel ano, Duration tempoLimite) {
            series.incrementAndGet();
            synchronized (anos) {
                anos.add(ano.ano());
            }
            entrou.countDown();
            if (segurar != null) {
                try {
                    segurar.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (falhar) {
                throw new IllegalStateException("falha fictícia");
            }
            return new LeituraDaSerie(List.of(ContagemDoMes.de(YearMonth.of(ano.ano(), 1), quantosRequerimentos)),
                    List.of(), 0);
        }

        @Override
        public BaseDoAno lerBase(AnoDoPainel ano, int limite, Duration tempoLimite) {
            bases.incrementAndGet();
            int mandar = Math.min(quantosRequerimentos, limite);
            List<RequerimentoDaBase> lista = IntStream.rangeClosed(1, mandar)
                    .mapToObj(i -> new RequerimentoDaBase(i, Optional.of(INSTANCIA), Optional.of("EM_ANDAMENTO")))
                    .toList();
            return new BaseDoAno(lista, quantosRequerimentos > limite);
        }

        @Override
        public LeituraDasTarefas ler(List<Long> ids, Collection<String> chaves, Duration tempoLimite) {
            lotes.incrementAndGet();
            return new LeituraDasTarefas(ids.stream().map(id -> new TarefaAtiva(id, MODELO, "taskFicticia",
                    ResponsavelDaTarefa.NAO_ATRIBUIDA, Optional.empty(), List.of())).toList(), Set.copyOf(ids), false);
        }
    }

    private static final class Cenario {
        final Portas portas = new Portas();
        final Parametros parametros = new Parametros();
        final GuardarInstantaneo guardar = new GuardarInstantaneo();
        // 2026-12-31 22:00 em Cuiabá (UTC-4) = 2027-01-01 02:00Z: o ano de Cuiabá ainda é 2026.
        final RelogioMutavel relogio = new RelogioMutavel(Instant.parse("2027-01-01T02:00:00Z"));
        final AtualizarPainel atualizar;
        final ConsultarPainel consultar;
        final SolicitarAtualizacao solicitar;

        Cenario(java.util.concurrent.Executor executor) {
            ParametrosDoRastro rastro = mock(ParametrosDoRastro.class);
            when(rastro.mostrarNomeDoResponsavel()).thenReturn(true);
            when(rastro.leiturasSimultaneasDoCamunda()).thenReturn(4);
            when(rastro.camundaTimeoutSegundos()).thenReturn(10);
            LerTarefasEmLotes leitor = new LerTarefasEmLotes(portas, new LimiteDeLeiturasDoCamunda(rastro));
            atualizar = new AtualizarPainel(portas, leitor, CATALOGO, GRUPOS, parametros, guardar, relogio, executor,
                    System::nanoTime);
            consultar = new ConsultarPainel(guardar, atualizar, rastro);
            solicitar = new SolicitarAtualizacao(atualizar, parametros, relogio);
        }

        static Cenario sincrono() {
            return new Cenario(Runnable::run);
        }
    }

    @Test
    void primeiraRequisicaoRespondePreparandoEFazUmaSoLeituraMesmoCom20Simultaneas() throws Exception {
        ExecutorService segundoPlano = Executors.newSingleThreadExecutor();
        ExecutorService requisicoes = Executors.newFixedThreadPool(20);
        try {
            Cenario c = new Cenario(segundoPlano);
            c.portas.segurar = new CountDownLatch(1);
            CountDownLatch largada = new CountDownLatch(1);
            List<java.util.concurrent.Future<EstadoDoPainel>> respostas = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                respostas.add(requisicoes.submit(() -> {
                    largada.await();
                    return c.consultar.consultar().instantaneo().estado();
                }));
            }
            largada.countDown();
            for (var r : respostas) {
                assertThat(r.get(10, TimeUnit.SECONDS)).isEqualTo(EstadoDoPainel.PREPARANDO);
            }
            assertThat(c.portas.entrou.await(10, TimeUnit.SECONDS)).isTrue();
            c.portas.segurar.countDown();
            esperarEstado(c, EstadoDoPainel.PRONTO);
            assertThat(c.portas.series.get()).isEqualTo(1);
            assertThat(c.portas.bases.get()).isEqualTo(1);
        } finally {
            segundoPlano.shutdownNow();
            requisicoes.shutdownNow();
        }
    }

    private static void esperarEstado(Cenario c, EstadoDoPainel esperado) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (c.guardar.atual().estado() != esperado && System.nanoTime() < limite) {
            Thread.sleep(10);
        }
        assertThat(c.guardar.atual().estado()).isEqualTo(esperado);
    }

    @Test
    void sucessoViraProntoComOAnoDeCuiabaESemTrocarNaVirada() {
        Cenario c = Cenario.sincrono();
        assertThat(c.consultar.consultar().instantaneo().estado()).isEqualTo(EstadoDoPainel.PRONTO);

        InstantaneoDoPainel i = c.guardar.atual();
        assertThat(i.ano()).isEqualTo(2026);
        assertThat(i.geradoEm()).contains(c.relogio.agora);
        assertThat(i.serie().orElseThrow().totalProtocolados()).isEqualTo(3);
        assertThat(i.fila().orElseThrow().totalNaFila()).isEqualTo(3);
        assertThat(i.truncado()).isFalse();
    }

    @Test
    void falhaDepoisDeSucessoMantemOUltimoBomComoDesatualizado() {
        Cenario c = Cenario.sincrono();
        c.consultar.consultar();
        InstantaneoDoPainel bom = c.guardar.atual();
        c.portas.falhar = true;
        c.relogio.agora = c.relogio.agora.plusSeconds(3600);

        c.atualizar.iniciarEmSegundoPlano();

        InstantaneoDoPainel i = c.guardar.atual();
        assertThat(i.estado()).isEqualTo(EstadoDoPainel.DESATUALIZADO);
        assertThat(i.geradoEm()).isEqualTo(bom.geradoEm());
        assertThat(i.falhouEm()).contains(c.relogio.agora);
        assertThat(i.serie()).isEqualTo(bom.serie());
        assertThat(c.atualizar.emAndamento()).isFalse();
    }

    @Test
    void falhaSemSucessoAnteriorEIndisponivel() {
        Cenario c = Cenario.sincrono();
        c.portas.falhar = true;

        InstantaneoDoPainel resposta = c.consultar.consultar().instantaneo();

        assertThat(resposta.estado()).isEqualTo(EstadoDoPainel.INDISPONIVEL);
        assertThat(resposta.serie()).isEmpty();
        assertThat(c.portas.bases.get()).isZero();
    }

    @Test
    void passarDoTetoMarcaTruncadoComAviso() {
        Cenario c = Cenario.sincrono();
        c.parametros.limite = 100;
        c.portas.quantosRequerimentos = 150;

        c.consultar.consultar();

        InstantaneoDoPainel i = c.guardar.atual();
        assertThat(i.estado()).isEqualTo(EstadoDoPainel.PRONTO);
        assertThat(i.truncado()).isTrue();
        assertThat(i.avisos()).extracting(a -> a.codigo()).contains("TRUNCADO");
        assertThat(i.avisos().stream().filter(a -> a.codigo().equals("TRUNCADO")).findFirst().orElseThrow().texto())
                .contains("100 primeiros requerimentos");
        assertThat(i.fila().orElseThrow().totalNaFila()).isEqualTo(100);
        // a série (gráfico 1) não depende do teto
        assertThat(i.serie().orElseThrow().totalProtocolados()).isEqualTo(150);
    }

    @Test
    void manualRecusadaAntesDoIntervaloEAceitaDepois() {
        Cenario c = Cenario.sincrono();
        c.consultar.consultar();
        assertThat(c.portas.series.get()).isEqualTo(1);

        c.relogio.agora = c.relogio.agora.plusSeconds(30);
        var recusada = c.solicitar.solicitar();
        assertThat(recusada.aceita()).isFalse();
        assertThat(recusada.tentarEmSegundos()).isEqualTo(90);
        assertThat(c.portas.series.get()).isEqualTo(1);

        c.relogio.agora = c.relogio.agora.plusSeconds(90);
        assertThat(c.solicitar.solicitar().aceita()).isTrue();
        assertThat(c.portas.series.get()).isEqualTo(2);
    }

    @Test
    void atualizacaoLentaNaoBloqueiaALeituraDoInstantaneo() throws Exception {
        ExecutorService segundoPlano = Executors.newSingleThreadExecutor();
        try {
            Cenario c = new Cenario(segundoPlano);
            c.consultar.consultar();
            esperarEstado(c, EstadoDoPainel.PRONTO);
            InstantaneoDoPainel bom = c.guardar.atual();

            c.portas.entrou = new CountDownLatch(1);
            c.portas.segurar = new CountDownLatch(1);
            c.relogio.agora = c.relogio.agora.plusSeconds(3600);
            assertThat(c.solicitar.solicitar().aceita()).isTrue();
            assertThat(c.portas.entrou.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(c.atualizar.emAndamento()).isTrue();
            long inicio = System.nanoTime();
            assertThat(c.consultar.consultar().instantaneo()).isSameAs(bom);
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
            // pedido manual durante a atualização não dispara outra
            assertThat(c.solicitar.solicitar().aceita()).isTrue();
            c.portas.segurar.countDown();
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (c.atualizar.emAndamento() && System.nanoTime() < limite) {
                Thread.sleep(10);
            }
            assertThat(c.portas.series.get()).isEqualTo(2);
        } finally {
            segundoPlano.shutdownNow();
        }
    }

    @Test
    void requisicaoNuncaChamaAsPortas() {
        // executor que não roda nada: só a leitura do instantâneo acontece
        List<Runnable> pendentes = new ArrayList<>();
        Cenario c = new Cenario(pendentes::add);

        for (int i = 0; i < 10; i++) {
            assertThat(c.consultar.consultar().instantaneo().estado()).isEqualTo(EstadoDoPainel.PREPARANDO);
        }
        assertThat(c.portas.total()).isZero();
        assertThat(pendentes).hasSize(1);

        pendentes.get(0).run();
        int depois = c.portas.total();
        for (int i = 0; i < 10; i++) {
            assertThat(c.consultar.consultar().instantaneo().estado()).isEqualTo(EstadoDoPainel.PRONTO);
        }
        assertThat(c.portas.total()).isEqualTo(depois);
        assertThat(pendentes).hasSize(1);
    }

    @Test
    void agendamentoSoAtualizaDepoisDaPrimeiraLeituraEPassadoOIntervalo() {
        Cenario c = Cenario.sincrono();
        c.atualizar.atualizarSeVencida();
        assertThat(c.portas.total()).isZero();

        c.consultar.consultar();
        int primeira = c.portas.series.get();
        c.relogio.agora = c.relogio.agora.plus(Duration.ofMinutes(29));
        c.atualizar.atualizarSeVencida();
        assertThat(c.portas.series.get()).isEqualTo(primeira);

        c.relogio.agora = c.relogio.agora.plus(Duration.ofMinutes(1));
        c.atualizar.atualizarSeVencida();
        assertThat(c.portas.series.get()).isEqualTo(primeira + 1);
    }

    @Test
    void parametrosSaoLidosACadaCicloEAMudancaDoAnoValeNoProximo() {
        Cenario c = Cenario.sincrono();
        c.consultar.consultar();
        assertThat(c.guardar.atual().ano()).isEqualTo(2026);

        c.parametros.ano = 2025;
        c.relogio.agora = c.relogio.agora.plusSeconds(3600);
        assertThat(c.solicitar.solicitar().aceita()).isTrue();

        assertThat(c.guardar.atual().ano()).isEqualTo(2025);
        assertThat(c.portas.anos).containsExactly(2026, 2025);
    }

    @Test
    void naViradaDoAnoEmCuiabaOAnoCorrenteMuda() {
        Cenario c = Cenario.sincrono();
        c.relogio.agora = Instant.parse("2027-01-01T04:00:00Z"); // 00:00 de 1º/1/2027 em Cuiabá
        c.consultar.consultar();
        assertThat(c.guardar.atual().ano()).isEqualTo(2027);
    }
}
