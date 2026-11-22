package br.gov.mt.sema.rastro.tramitacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.tramitacao.application.port.CacheDaTramitacao;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaPassagem;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento.Estado;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Leitura protegida da tramitação (S8, bloco 7), sem banco: cache e TTL, {@code INDISPONIVEL} fora do cache, versão do
 * catálogo na chave, uma leitura por requerimento com pedidos simultâneos, tempo total, limite de leituras ao Camunda e o
 * parâmetro de nome aplicado depois do cache.
 */
class LerTramitacaoTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 8, 0);

    private final ParametrosDoRastroDeTeste parametros = new ParametrosDoRastroDeTeste();
    private final MontarTramitacao montar = mock(MontarTramitacao.class);
    private final CacheComRelogio cache = new CacheComRelogio();
    private final AtomicInteger leituras = new AtomicInteger();
    private String versao = "v1";
    private LerTramitacao ler;

    @AfterEach
    void fechar() {
        if (ler != null) {
            ler.encerrar();
        }
    }

    /** Cache em mapa com relógio controlado pelo teste (segundos). */
    private static final class CacheComRelogio implements CacheDaTramitacao {
        final Map<String, Object[]> mapa = new HashMap<>();
        long agora;

        @Override
        public synchronized Optional<TramitacaoDoRequerimento> obter(long id, String versao) {
            Object[] e = mapa.get(id + "@" + versao);
            if (e == null || agora >= (long) e[1]) {
                return Optional.empty();
            }
            return Optional.of((TramitacaoDoRequerimento) e[0]);
        }

        @Override
        public synchronized void guardar(long id, String versao, TramitacaoDoRequerimento t, Duration validade) {
            if (t.estado() != Estado.INDISPONIVEL) {
                mapa.put(id + "@" + versao, new Object[] {t, agora + validade.toSeconds()});
            }
        }
    }

    private LerTramitacao criar() {
        CatalogoDeEtapas real = CatalogoDeEtapasDeTeste.padrao();
        CatalogoDeEtapas comVersao = new CatalogoDeEtapas() {
            @Override
            public List<ModeloDeFluxo> modelos() {
                return real.modelos();
            }

            @Override
            public Optional<ModeloDeFluxo> modelo(String chave) {
                return real.modelo(chave);
            }

            @Override
            public Optional<ModeloDeFluxo> modeloDaInstancia(String instancia) {
                return real.modeloDaInstancia(instancia);
            }

            @Override
            public String versao() {
                return versao;
            }
        };
        ler = new LerTramitacao(montar, cache, comVersao, parametros, new LimiteDeLeiturasDoCamunda(parametros));
        return ler;
    }

    private static TramitacaoDoRequerimento ativa() {
        ResponsavelDaPassagem analista = new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.SERVIDOR, "Analista",
                Optional.of("Servidor Exemplo 05"), T0, null, ResponsavelDaPassagem.Estado.PENDENTE);
        PassagemPelaEtapa p = new PassagemPelaEtapa(Optional.empty(), "taskX", 1, Ramo.PRINCIPAL, T0, Optional.empty(),
                List.of(analista), false, List.of(), Optional.empty(), false, 1, false);
        CaminhoReconstruido caminho = new CaminhoReconstruido(List.of(p), List.of(),
                CaminhoReconstruido.ClassificacaoDaTaxa.INDETERMINADA, Optional.empty(), Set.of(), 0, Set.of(), false, 1);
        return new TramitacaoDoRequerimento(Estado.ATIVO, Optional.of("procOutorgaAguaSupSub"), Optional.empty(),
                caminho, Optional.empty(), Optional.empty(), List.of());
    }

    private void montarDevolve(TramitacaoDoRequerimento t, long esperaMs) {
        when(montar.montar(anyLong())).thenAnswer(c -> {
            leituras.incrementAndGet();
            if (esperaMs > 0) {
                Thread.sleep(esperaMs);
            }
            return t;
        });
    }

    @Test
    void cache_serve_dentro_da_validade_e_rele_depois() {
        parametros.cacheTramitacaoSegundos = 30;
        montarDevolve(ativa(), 0);
        LerTramitacao l = criar();
        l.ler(8001);
        l.ler(8001);
        assertThat(leituras).hasValue(1);
        cache.agora = 31;
        l.ler(8001);
        assertThat(leituras).hasValue(2);
        l.ler(8002);
        assertThat(leituras).hasValue(3);
    }

    @Test
    void cache_desligado_le_sempre() {
        parametros.cacheTramitacaoSegundos = 0;
        montarDevolve(ativa(), 0);
        LerTramitacao l = criar();
        l.ler(8001);
        l.ler(8001);
        assertThat(leituras).hasValue(2);
    }

    @Test
    void indisponivel_nunca_entra_no_cache() {
        parametros.cacheTramitacaoSegundos = 30;
        montarDevolve(TramitacaoDoRequerimento.indisponivel(Optional.empty(), Optional.empty()), 0);
        LerTramitacao l = criar();
        assertThat(l.ler(8001).estado()).isEqualTo(Estado.INDISPONIVEL);
        assertThat(l.ler(8001).estado()).isEqualTo(Estado.INDISPONIVEL);
        assertThat(leituras).hasValue(2);
        assertThat(cache.mapa).isEmpty();
    }

    @Test
    void mudar_o_catalogo_invalida_o_cache() {
        parametros.cacheTramitacaoSegundos = 30;
        montarDevolve(ativa(), 0);
        LerTramitacao l = criar();
        l.ler(8001);
        versao = "v2";
        l.ler(8001);
        assertThat(leituras).hasValue(2);
    }

    @Test
    void varios_pedidos_simultaneos_do_mesmo_requerimento_fazem_uma_leitura() throws Exception {
        parametros.cacheTramitacaoSegundos = 30;
        montarDevolve(ativa(), 300);
        LerTramitacao l = criar();
        List<TramitacaoDoRequerimento> resultados = emParalelo(20, () -> l.ler(8001));
        assertThat(leituras).hasValue(1);
        assertThat(resultados).hasSize(20).allSatisfy(t -> assertThat(t.estado()).isEqualTo(Estado.ATIVO));
    }

    @Test
    void single_flight_vale_mesmo_com_cache_desligado() throws Exception {
        parametros.cacheTramitacaoSegundos = 0;
        montarDevolve(ativa(), 300);
        LerTramitacao l = criar();
        emParalelo(10, () -> l.ler(8001));
        assertThat(leituras).hasValue(1);
    }

    @Test
    void camunda_lento_vira_indisponivel_dentro_do_tempo_total_e_a_leitura_tardia_ainda_alimenta_o_cache()
            throws Exception {
        parametros.cacheTramitacaoSegundos = 30;
        parametros.tramitacaoTimeoutSegundos = 1;
        montarDevolve(ativa(), 2_000);
        LerTramitacao l = criar();
        long inicio = System.nanoTime();
        TramitacaoDoRequerimento t = l.ler(8001);
        long decorridoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        assertThat(t.estado()).isEqualTo(Estado.INDISPONIVEL);
        assertThat(t.planoB()).isEmpty();
        assertThat(decorridoMs).isLessThan(1_800);
        Thread.sleep(1_500);
        assertThat(l.ler(8001).estado()).isEqualTo(Estado.ATIVO);
        assertThat(leituras).hasValue(1);
    }

    @Test
    void leituras_ao_camunda_respeitam_o_limite_de_simultaneas() throws Exception {
        parametros.cacheTramitacaoSegundos = 0;
        parametros.leiturasSimultaneas = 2;
        AtomicInteger agora = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        when(montar.montar(anyLong())).thenAnswer(c -> {
            int n = agora.incrementAndGet();
            maximo.accumulateAndGet(n, Math::max);
            Thread.sleep(200);
            agora.decrementAndGet();
            return ativa();
        });
        LerTramitacao l = criar();
        AtomicInteger id = new AtomicInteger(9000);
        List<TramitacaoDoRequerimento> resultados = emParalelo(8, () -> l.ler(id.incrementAndGet()));
        assertThat(maximo.get()).isEqualTo(2);
        assertThat(resultados).allSatisfy(t -> assertThat(t.estado()).isEqualTo(Estado.ATIVO));
    }

    @Test
    void sem_vaga_dentro_do_tempo_total_e_indisponivel() throws Exception {
        parametros.cacheTramitacaoSegundos = 0;
        parametros.leiturasSimultaneas = 1;
        parametros.tramitacaoTimeoutSegundos = 1;
        when(montar.montar(anyLong())).thenAnswer(c -> {
            Thread.sleep(2_500);
            return ativa();
        });
        LerTramitacao l = criar();
        ExecutorService fora = Executors.newSingleThreadExecutor();
        try {
            fora.submit(() -> l.ler(1));
            Thread.sleep(100);
            assertThat(l.ler(2).estado()).isEqualTo(Estado.INDISPONIVEL);
        } finally {
            fora.shutdownNow();
        }
    }

    @Test
    void nome_do_servidor_e_aplicado_depois_do_cache() {
        parametros.cacheTramitacaoSegundos = 30;
        montarDevolve(ativa(), 0);
        LerTramitacao l = criar();
        assertThat(nome(l.ler(8001))).contains("Servidor Exemplo 05");
        parametros.mostrarNome = false;
        assertThat(nome(l.ler(8001))).isEmpty();
        parametros.mostrarNome = true;
        assertThat(nome(l.ler(8001))).contains("Servidor Exemplo 05");
        assertThat(leituras).hasValue(1);
    }

    private static Optional<String> nome(TramitacaoDoRequerimento t) {
        return t.caminho().passagens().getFirst().responsaveis().getFirst().nome();
    }

    private static <T> List<T> emParalelo(int n, Callable<T> tarefa) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> f : futuros) {
                resultados.add(f.get());
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}
