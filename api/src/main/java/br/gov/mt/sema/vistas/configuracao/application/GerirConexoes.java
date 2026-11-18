package br.gov.mt.sema.vistas.configuracao.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import br.gov.mt.sema.vistas.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.vistas.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.vistas.configuracao.application.port.VerificadorDeConexao.Estado;
import br.gov.mt.sema.vistas.configuracao.application.port.VerificadorDeConexao.Resultado;
import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;
import br.gov.mt.sema.vistas.shared.application.exception.RecursoNaoEncontradoException;
import br.gov.mt.sema.vistas.shared.application.exception.VistasException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Aba "Conexões": só Administrador. A conexão é escolhida numa lista fixa do servidor, pelo nome: o pedido nunca informa
 * destino, então o teste não serve de SSRF. Um teste por conexão de cada vez e tempo limite curto.
 */
@Service
public class GerirConexoes {

    static final Duration TEMPO_LIMITE = Duration.ofSeconds(10);
    private static final Logger log = LoggerFactory.getLogger(GerirConexoes.class);

    private final List<VerificadorDeConexao> verificadores;
    private final PoliticaDeAcesso politica;
    private final Duration tempoLimite;
    private final Map<String, Semaphore> vagas = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public GerirConexoes(List<VerificadorDeConexao> verificadores, PoliticaDeAcesso politica) {
        this(verificadores, politica, TEMPO_LIMITE);
    }

    GerirConexoes(List<VerificadorDeConexao> verificadores, PoliticaDeAcesso politica, Duration tempoLimite) {
        this.verificadores = List.copyOf(verificadores);
        this.politica = politica;
        this.tempoLimite = tempoLimite;
    }

    public record SituacaoDaConexao(String nome, String rotulo, Estado estado, Long horasAteExpirar) {
    }

    public record TesteDaConexao(String nome, boolean sucesso, long tempoMs, String mensagem, Long horasAteExpirar) {
    }

    public List<SituacaoDaConexao> listar() {
        politica.exigirConfiguracoes();
        return verificadores.stream()
                .map(v -> new SituacaoDaConexao(v.nome(), v.rotulo(), v.estado(), horas(v))).toList();
    }

    public TesteDaConexao testar(String nome) {
        politica.exigirConfiguracoes();
        VerificadorDeConexao verificador = verificadores.stream().filter(v -> v.nome().equals(nome)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conexão não encontrada."));
        if (verificador.estado() != Estado.CONFIGURADA) {
            String mensagem = verificador.estado() == Estado.DESATIVADA ? "Conexão desativada." : "Conexão não configurada.";
            return new TesteDaConexao(nome, false, 0, mensagem, null);
        }
        Semaphore vaga = vagas.computeIfAbsent(nome, n -> new Semaphore(1));
        if (!vaga.tryAcquire()) {
            throw new VistasException(ErrorCode.TESTE_DE_CONEXAO_EM_ANDAMENTO, "Já há um teste desta conexão em andamento.");
        }
        try {
            long inicio = System.nanoTime();
            Resultado resultado = executar(verificador);
            long tempoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            return new TesteDaConexao(nome, resultado.sucesso(), tempoMs, resultado.mensagem(), horas(verificador));
        } finally {
            vaga.release();
        }
    }

    private Resultado executar(VerificadorDeConexao verificador) {
        Future<Resultado> futuro = executor.submit(verificador::verificar);
        try {
            return futuro.get(tempoLimite.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futuro.cancel(true);
            return new Resultado(false, "Tempo limite excedido.");
        } catch (InterruptedException e) {
            futuro.cancel(true);
            Thread.currentThread().interrupt();
            return new Resultado(false, "Teste interrompido.");
        } catch (ExecutionException e) {
            log.warn("Teste da conexão {} falhou: {}", verificador.nome(), e.getCause().getClass().getSimpleName());
            return new Resultado(false, "Não foi possível testar a conexão.");
        }
    }

    private Long horas(VerificadorDeConexao verificador) {
        try {
            return verificador.horasAteExpirar().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
