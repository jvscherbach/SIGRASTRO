package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import br.gov.mt.sema.rastro.tramitacao.application.LerTramitacao;
import br.gov.mt.sema.rastro.tramitacao.application.LimiteDeLeiturasDoCamunda;
import br.gov.mt.sema.rastro.tramitacao.application.MontarTramitacao;
import br.gov.mt.sema.rastro.tramitacao.application.port.CacheDaTramitacao;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento.Estado;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDoConfrontoDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.MapaDeEventosDeTeste;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de carga leve da tramitação (S8, bloco 7) com a pilha real (SQL de produção, montagem, cache, limite de leituras)
 * sobre o Oracle de teste e as fixtures da S8, pelo usuário de leitura: 200 requerimentos em sequência e 20 em paralelo,
 * sem estouro de limite, sem passar do teto de leituras simultâneas (medido em conexões abertas ao mesmo tempo) e com o
 * número de consultas por requerimento dentro do máximo documentado. O pool tem folga (10) de propósito: quem segura as
 * conexões é o limite de leituras, não o pool.
 */
@Testcontainers(disabledWithoutDocker = true)
class CargaDaTramitacaoTest {

    /** Teto documentado de consultas numa leitura da tramitação (docs/15, "Desempenho e limites"). */
    static final int CONSULTAS_NO_MAXIMO = 8;
    private static final int LEITURAS_SIMULTANEAS = 3;

    private static final long[] CENARIOS = {HC01, HC02, HC03, HC04, HC05, HC06, HC07, HC08, HC09, HC10, HC11, HC12, HC13,
            HC14, HC15, HF01, HF02, HF03, HF04, LC01, LC02, LC03, LF01, LF02, LF03, LF04, LF05, PB01, PB02, PB03};

    private static final AtomicInteger CONSULTAS = new AtomicInteger();
    private static final AtomicInteger ABERTAS = new AtomicInteger();
    private static final AtomicInteger PICO_DE_CONEXOES = new AtomicInteger();

    private static HikariDataSource pool;
    private static MontarTramitacao montar;
    private static volatile int cacheSegundos;
    private LerTramitacao ler;

    private static final ParametrosDoRastro PARAMETROS = new ParametrosDoRastro() {
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
            return 4;
        }

        @Override
        public int camundaTimeoutSegundos() {
            return 10;
        }

        @Override
        public int cacheDaTramitacaoSegundos() {
            return cacheSegundos;
        }

        @Override
        public int tramitacaoTimeoutSegundos() {
            return 60;
        }

        @Override
        public int leiturasSimultaneasDoCamunda() {
            return LEITURAS_SIMULTANEAS;
        }
    };

    /** Cache em mapa, sem expiração (a validade é coberta em LerTramitacaoTest e no adaptador). */
    private static final class CacheEmMapa implements CacheDaTramitacao {
        final Map<String, TramitacaoDoRequerimento> mapa = new ConcurrentHashMap<>();

        @Override
        public Optional<TramitacaoDoRequerimento> obter(long id, String versao) {
            return Optional.ofNullable(mapa.get(id + "@" + versao));
        }

        @Override
        public void guardar(long id, String versao, TramitacaoDoRequerimento t, Duration validade) {
            if (t.estado() != Estado.INDISPONIVEL) {
                mapa.put(id + "@" + versao, t);
            }
        }
    }

    @BeforeAll
    static void abrir() {
        assertThat(SigaOracleTeste.CENARIOS_S8).isNotEmpty();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(SigaOracleTeste.urlJdbc());
        config.setUsername(SigaOracleTeste.LEITURA);
        config.setPassword(SigaOracleTeste.SENHA_LEITURA);
        config.setReadOnly(true);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(0);
        pool = new HikariDataSource(config);
        CamundaPosicaoJdbc camunda = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(medindo(pool)), 10);
        montar = new MontarTramitacao(camunda, camunda, camunda, camunda, CatalogoDeEtapasDeTeste.padrao(),
                MapaDeEventosDeTeste.padrao(), CatalogoDoConfrontoDeTeste.padrao());
    }

    @AfterAll
    static void fechar() {
        pool.close();
    }

    @BeforeEach
    void zerar() {
        cacheSegundos = 0;
        CONSULTAS.set(0);
        PICO_DE_CONEXOES.set(0);
    }

    private LerTramitacao novo(CacheDaTramitacao cache) {
        return new LerTramitacao(montar, cache, CatalogoDeEtapasDeTeste.padrao(), PARAMETROS,
                new LimiteDeLeiturasDoCamunda(PARAMETROS));
    }

    @Test
    void duzentos_requerimentos_em_sequencia_sem_falha_e_com_consultas_limitadas() {
        LerTramitacao l = novo(new CacheEmMapa());
        int maiorPorLeitura = 0;
        for (int i = 0; i < 200; i++) {
            long id = CENARIOS[i % CENARIOS.length];
            int antes = CONSULTAS.get();
            TramitacaoDoRequerimento t = l.ler(id);
            maiorPorLeitura = Math.max(maiorPorLeitura, CONSULTAS.get() - antes);
            assertThat(t.estado()).as("requerimento " + id).isNotEqualTo(Estado.INDISPONIVEL);
            assertThat(t.caminho().truncado()).as("requerimento " + id).isFalse();
        }
        assertThat(maiorPorLeitura).isLessThanOrEqualTo(CONSULTAS_NO_MAXIMO);
        assertThat(PICO_DE_CONEXOES.get()).isEqualTo(1);
        l.encerrar();
    }

    @Test
    void vinte_em_paralelo_nao_passam_do_teto_de_leituras_simultaneas() throws Exception {
        LerTramitacao l = novo(new CacheEmMapa());
        List<TramitacaoDoRequerimento> resultados = emParalelo(20, n -> l.ler(CENARIOS[n % CENARIOS.length]));
        assertThat(resultados).hasSize(20)
                .allSatisfy(t -> assertThat(t.estado()).isNotEqualTo(Estado.INDISPONIVEL));
        assertThat(PICO_DE_CONEXOES.get()).isBetween(1, LEITURAS_SIMULTANEAS);
        l.encerrar();
    }

    @Test
    void vinte_pedidos_do_mesmo_requerimento_com_cache_fazem_uma_so_leitura() throws Exception {
        cacheSegundos = 60;
        LerTramitacao l = novo(new CacheEmMapa());
        CONSULTAS.set(0);
        List<TramitacaoDoRequerimento> resultados = emParalelo(20, n -> l.ler(HC12));
        assertThat(resultados).allSatisfy(t -> assertThat(t.estado()).isEqualTo(Estado.ATIVO));
        int umaLeitura = CONSULTAS.get();
        assertThat(umaLeitura).isBetween(1, CONSULTAS_NO_MAXIMO);
        l.ler(HC12);
        assertThat(CONSULTAS.get()).isEqualTo(umaLeitura);
        l.encerrar();
    }

    private interface Tarefa<T> {
        T executar(int n) throws Exception;
    }

    private static <T> List<T> emParalelo(int n, Tarefa<T> tarefa) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int numero = i;
                futuros.add(executor.submit(() -> {
                    largada.await();
                    return tarefa.executar(numero);
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> f : futuros) {
                resultados.add(f.get());
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }

    /** Conta comandos preparados e conexões abertas ao mesmo tempo. */
    private static DataSource medindo(DataSource alvo) {
        return (DataSource) Proxy.newProxyInstance(CargaDaTramitacaoTest.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, metodo, args) -> {
                    Object resultado = invocar(alvo, metodo, args);
                    if (resultado instanceof Connection conexao) {
                        PICO_DE_CONEXOES.accumulateAndGet(ABERTAS.incrementAndGet(), Math::max);
                        AtomicInteger fechada = new AtomicInteger();
                        return Proxy.newProxyInstance(CargaDaTramitacaoTest.class.getClassLoader(),
                                new Class<?>[] {Connection.class}, (p2, m2, a2) -> {
                                    if (m2.getName().startsWith("prepare") || m2.getName().equals("createStatement")) {
                                        CONSULTAS.incrementAndGet();
                                    }
                                    if (m2.getName().equals("close") && fechada.getAndIncrement() == 0) {
                                        ABERTAS.decrementAndGet();
                                    }
                                    return invocar(conexao, m2, a2);
                                });
                    }
                    return resultado;
                });
    }

    private static Object invocar(Object alvo, Method metodo, Object[] args) throws Throwable {
        try {
            return metodo.invoke(alvo, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
