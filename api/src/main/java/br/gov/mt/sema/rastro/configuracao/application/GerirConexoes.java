package br.gov.mt.sema.rastro.configuracao.application;

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

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Classificacao;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Estado;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao.Resultado;
import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import br.gov.mt.sema.rastro.shared.application.exception.RastroException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Aba "Conexões": só Administrador. A conexão é escolhida numa lista fixa do servidor, pelo nome: o pedido nunca informa
 * destino, então o teste não serve de SSRF. Um teste por conexão de cada vez.
 *
 * <p>S6, bloco 4: o resultado traz a classificação (OK, alcançável com ressalva, falha de autenticação, falha), o código
 * HTTP quando houve resposta, o tempo medido e o tempo limite do teste ({@code CONEXAO_TESTE_TIMEOUT_SEGUNDOS}, padrão
 * 15). Cada teste vira um evento {@code CONEXAO} na auditoria, só com a conexão e o resultado.
 */
@Service
public class GerirConexoes {

    static final int TEMPO_LIMITE_PADRAO_SEGUNDOS = 15;
    private static final Logger log = LoggerFactory.getLogger(GerirConexoes.class);

    private final List<VerificadorDeConexao> verificadores;
    private final PoliticaDeAcesso politica;
    private final int tempoDoTesteSegundos;
    private final Duration tempoLimiteDoServico;
    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;
    private final Map<String, Semaphore> vagas = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public GerirConexoes(List<VerificadorDeConexao> verificadores, PoliticaDeAcesso politica,
            @Value("${rastro.conexoes.teste-timeout-segundos:15}") int tempoDoTesteSegundos, RegistrarEvento registrar,
            ContextoDoEvento contexto) {
        this(verificadores, politica, Math.max(1, tempoDoTesteSegundos), teto(Math.max(1, tempoDoTesteSegundos)),
                registrar, contexto);
    }

    GerirConexoes(List<VerificadorDeConexao> verificadores, PoliticaDeAcesso politica, int tempoDoTesteSegundos,
            Duration tempoLimiteDoServico, RegistrarEvento registrar, ContextoDoEvento contexto) {
        this.verificadores = List.copyOf(verificadores);
        this.politica = politica;
        this.tempoDoTesteSegundos = tempoDoTesteSegundos;
        this.tempoLimiteDoServico = tempoLimiteDoServico;
        this.registrar = registrar;
        this.contexto = contexto;
    }

    /** Teto do teste inteiro: duas chamadas (503 isolado ganha uma nova tentativa), a espera entre elas e folga. */
    static Duration teto(int tempoDoTesteSegundos) {
        return Duration.ofSeconds(2L * tempoDoTesteSegundos + 5);
    }

    /** @param tempoLimiteDoTesteSegundos mostrado na tela antes de testar */
    public record Conexoes(List<SituacaoDaConexao> conexoes, int tempoLimiteDoTesteSegundos) {
    }

    public record SituacaoDaConexao(String nome, String rotulo, Estado estado, Long horasAteExpirar) {
    }

    /**
     * @param sucesso    o serviço respondeu (OK ou alcançável com ressalva)
     * @param codigoHttp status da resposta do serviço; nulo sem resposta HTTP
     */
    public record TesteDaConexao(String nome, boolean sucesso, Classificacao classificacao, Integer codigoHttp,
            long tempoMs, int tempoLimiteSegundos, String mensagem, Long horasAteExpirar) {
    }

    public Conexoes listar() {
        politica.exigirConfiguracoes();
        return new Conexoes(verificadores.stream()
                .map(v -> new SituacaoDaConexao(v.nome(), v.rotulo(), v.estado(), horas(v))).toList(),
                tempoDoTesteSegundos);
    }

    public TesteDaConexao testar(String nome) {
        politica.exigirConfiguracoes();
        VerificadorDeConexao verificador = verificadores.stream().filter(v -> v.nome().equals(nome)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conexão não encontrada."));
        if (verificador.estado() != Estado.CONFIGURADA) {
            String mensagem = verificador.estado() == Estado.DESATIVADA ? "Conexão desativada." : "Conexão não configurada.";
            return new TesteDaConexao(nome, false, Classificacao.FALHA, null, 0, tempoDoTesteSegundos, mensagem, null);
        }
        Semaphore vaga = vagas.computeIfAbsent(nome, n -> new Semaphore(1));
        if (!vaga.tryAcquire()) {
            throw new RastroException(ErrorCode.TESTE_DE_CONEXAO_EM_ANDAMENTO, "Já há um teste desta conexão em andamento.");
        }
        try {
            long inicio = System.nanoTime();
            Resultado resultado = executar(verificador);
            long tempoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            auditar(verificador, resultado, tempoMs);
            return new TesteDaConexao(nome, resultado.sucesso(), resultado.classificacao(), resultado.codigoHttp(),
                    tempoMs, tempoDoTesteSegundos, resultado.mensagem(), horas(verificador));
        } finally {
            vaga.release();
        }
    }

    private Resultado executar(VerificadorDeConexao verificador) {
        Future<Resultado> futuro = executor.submit(verificador::verificar);
        try {
            return futuro.get(tempoLimiteDoServico.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futuro.cancel(true);
            return Resultado.falha("Tempo limite do teste (" + tempoDoTesteSegundos + " s) excedido.");
        } catch (InterruptedException e) {
            futuro.cancel(true);
            Thread.currentThread().interrupt();
            return Resultado.falha("Teste interrompido.");
        } catch (ExecutionException e) {
            log.warn("Teste da conexão {} falhou: {}", verificador.nome(), e.getCause().getClass().getSimpleName());
            return Resultado.falha("Não foi possível testar a conexão.");
        }
    }

    /** Só a conexão e o resultado: nada de URL, host, token, cookie nem corpo do serviço. Nunca derruba o teste. */
    private void auditar(VerificadorDeConexao verificador, Resultado resultado, long tempoMs) {
        try {
            String http = resultado.codigoHttp() == null ? "" : " (HTTP " + resultado.codigoHttp() + ")";
            registrar.registrar(contexto.novo(TipoDeEvento.CONEXAO)
                    .detalhe(verificador.rotulo() + ": " + resultado.classificacao().name() + http + ", " + tempoMs + " ms")
                    .criar());
        } catch (RuntimeException e) {
            log.warn("Não foi possível registrar o teste da conexão {} na auditoria ({})", verificador.nome(),
                    e.getClass().getSimpleName());
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
