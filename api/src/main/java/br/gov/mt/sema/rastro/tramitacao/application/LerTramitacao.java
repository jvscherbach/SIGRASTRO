package br.gov.mt.sema.rastro.tramitacao.application;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.tramitacao.application.port.CacheDaTramitacao;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Leitura protegida da tramitação (S8, bloco 7), entre {@link AbrirTramitacao} (que já conferiu o acesso) e
 * {@link MontarTramitacao}. Protege o Camunda de produção:
 *
 * <ol>
 *   <li><b>Cache</b> em memória ({@code RASTRO_TRAMITACAO_CACHE_SEGUNDOS}, padrão 60; 0 desliga), chave = id + versão do
 *       catálogo de etapas (a mesma ideia da posição: mudou o catálogo, as duas deixam de servir o antigo). Teto de
 *       entradas no adaptador. {@code INDISPONIVEL} nunca entra.</li>
 *   <li><b>Uma leitura por requerimento</b> mesmo com vários pedidos ao mesmo tempo (<i>single flight</i>): quem chega
 *       enquanto a leitura está em andamento espera a mesma.</li>
 *   <li><b>Tempo total</b> por pedido ({@code RASTRO_TRAMITACAO_TIMEOUT_SEGUNDOS}, padrão 20), contando a espera por vaga;
 *       estourado, {@code INDISPONIVEL} na hora. A leitura que ficou para trás termina sozinha (cada consulta tem o
 *       próprio tempo limite, {@code RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS}) e ainda alimenta o cache.</li>
 *   <li><b>Leituras simultâneas</b> ao Camunda limitadas por {@link LimiteDeLeiturasDoCamunda} (o mesmo limite da
 *       posição).</li>
 *   <li><b>Nome do servidor</b> ({@code RASTRO_MOSTRAR_NOME_RESPONSAVEL}) aplicado <b>depois</b> do cache, para valer na
 *       próxima leitura; o RN12 do Operador, depois disto, em {@link AbrirTramitacao}.</li>
 * </ol>
 *
 * <p>O plano B nunca substitui uma falha: tempo estourado, sem vaga ou erro são {@code INDISPONIVEL}.
 */
@Service
public class LerTramitacao {

    private static final Logger log = LoggerFactory.getLogger(LerTramitacao.class);

    /** Teto de threads de leitura (as que passam do limite de leituras simultâneas só esperam a vez). */
    static final int MAXIMO_DE_THREADS = 32;

    private record Chave(long idRequerimento, String versaoDoCatalogo) {}

    private final MontarTramitacao montar;
    private final CacheDaTramitacao cache;
    private final CatalogoDeEtapas catalogo;
    private final ParametrosDoRastro parametros;
    private final LimiteDeLeiturasDoCamunda limite;
    private final ConcurrentHashMap<Chave, CompletableFuture<TramitacaoDoRequerimento>> emAndamento =
            new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public LerTramitacao(MontarTramitacao montar, CacheDaTramitacao cache, CatalogoDeEtapas catalogo,
            ParametrosDoRastro parametros, LimiteDeLeiturasDoCamunda limite) {
        this.montar = montar;
        this.cache = cache;
        this.catalogo = catalogo;
        this.parametros = parametros;
        this.limite = limite;
        AtomicInteger numero = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(0, MAXIMO_DE_THREADS, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
                tarefa -> {
                    Thread t = new Thread(tarefa, "rastro-tramitacao-" + numero.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
    }

    @PreDestroy
    public void encerrar() {
        executor.shutdownNow();
    }

    public TramitacaoDoRequerimento ler(long idRequerimento) {
        TramitacaoDoRequerimento lida = doCacheOuDoCamunda(idRequerimento);
        return parametros.mostrarNomeDoResponsavel() ? lida : lida.semNomes();
    }

    private TramitacaoDoRequerimento doCacheOuDoCamunda(long idRequerimento) {
        Chave chave = new Chave(idRequerimento, catalogo.versao());
        int validade = parametros.cacheDaTramitacaoSegundos();
        if (validade > 0) {
            Optional<TramitacaoDoRequerimento> guardada = cache.obter(chave.idRequerimento(), chave.versaoDoCatalogo());
            if (guardada.isPresent()) {
                return guardada.get();
            }
        }
        Duration total = Duration.ofSeconds(Math.max(1, parametros.tramitacaoTimeoutSegundos()));
        CompletableFuture<TramitacaoDoRequerimento> novo = new CompletableFuture<>();
        CompletableFuture<TramitacaoDoRequerimento> existente = emAndamento.putIfAbsent(chave, novo);
        CompletableFuture<TramitacaoDoRequerimento> futuro = existente != null ? existente : novo;
        if (existente == null && !iniciar(chave, novo, total, validade)) {
            return indisponivel(idRequerimento, "sem thread livre");
        }
        try {
            return futuro.get(total.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            return indisponivel(idRequerimento, "tempo total estourado");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return indisponivel(idRequerimento, "interrompida");
        } catch (ExecutionException e) {
            return indisponivel(idRequerimento, e.getCause() == null ? "falha" : e.getCause().getClass().getSimpleName());
        }
    }

    private boolean iniciar(Chave chave, CompletableFuture<TramitacaoDoRequerimento> futuro, Duration total,
            int validade) {
        try {
            executor.execute(() -> {
                // Sai do "em andamento" ANTES de completar: quem chega depois já não pega um resultado pronto (que pode
                // ser INDISPONIVEL) e vai ao cache ou faz a própria leitura.
                try {
                    TramitacaoDoRequerimento lida = limite.executar(total, () -> montar.montar(chave.idRequerimento()));
                    if (validade > 0) {
                        cache.guardar(chave.idRequerimento(), chave.versaoDoCatalogo(), lida, Duration.ofSeconds(validade));
                    }
                    emAndamento.remove(chave, futuro);
                    futuro.complete(lida);
                } catch (RuntimeException e) {
                    emAndamento.remove(chave, futuro);
                    futuro.completeExceptionally(e);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            emAndamento.remove(chave, futuro);
            futuro.completeExceptionally(e);
            return false;
        }
    }

    /** Sem a mensagem da exceção: o texto do driver pode trazer valores de parâmetros. */
    private static TramitacaoDoRequerimento indisponivel(long idRequerimento, String motivo) {
        log.warn("Tramitação do requerimento {} indisponível ({})", idRequerimento, motivo);
        return TramitacaoDoRequerimento.indisponivel(Optional.empty(), Optional.empty());
    }
}
