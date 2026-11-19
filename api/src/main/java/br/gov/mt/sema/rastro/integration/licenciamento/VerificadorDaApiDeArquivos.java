package br.gov.mt.sema.rastro.integration.licenciamento;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * Teste da API de arquivos (S6, bloco 4): um {@code GET} numa chave fictícia, sem ler corpo, com a <b>mesma</b> base de
 * URL, o mesmo caminho, a mesma autenticação e o mesmo tipo de cliente do download real ({@link AcessoAApiDeArquivos}),
 * mudando só a chave. A API não tem rota de saúde conhecida (R55). Token vencido nem é enviado.
 *
 * <p>Resultado ({@link VerificadorDeConexao.Classificacao}): 2xx OK; 400/404 e outros 4xx, alcançável com ressalva (a
 * chave é fictícia); 401/403, falha de autenticação; 503 isolado ganha uma nova tentativa (como o download real, que
 * tenta de novo) e só é falha se repetir; DNS, recusa, TLS e tempo esgotado são falha. Tempo limite do teste em
 * {@code CONEXAO_TESTE_TIMEOUT_SEGUNDOS} (padrão 15; antes eram 5 s fixos, menos que o do download real).
 */
@Component
@Order(2)
class VerificadorDaApiDeArquivos implements VerificadorDeConexao {

    private static final Logger log = LoggerFactory.getLogger(VerificadorDaApiDeArquivos.class);

    /** Chave que não existe: o serviço a recusa de forma conhecida, sem efeito colateral. */
    static final String CHAVE_FICTICIA = "verificacao-de-conexao";
    static final String CAMINHO = AcessoAApiDeArquivos.CAMINHO_DO_ARQUIVO + CHAVE_FICTICIA;
    /** Mesma primeira espera do download real antes da nova tentativa. */
    static final Duration ESPERA_DA_NOVA_TENTATIVA = ArquivosDoLicenciamentoHttp.ESPERAS.get(0);

    /** Pausa antes da nova tentativa; nos testes, não espera de verdade. */
    interface Espera {
        void aguardar(Duration duracao) throws InterruptedException;
    }

    private final AcessoAApiDeArquivos acesso;
    private final FonteDoToken token;
    private final Clock relogio;
    private final Duration tempoLimite;
    private final Espera espera;

    @Autowired
    VerificadorDaApiDeArquivos(@Value("${rastro.arquivos.url:}") String url,
            @Value("${rastro.arquivos.token:}") String token,
            @Value("${rastro.arquivos.token-file:}") String arquivoDoToken,
            @Value("${rastro.arquivos.auth-mode:bearer}") String modo,
            @Value("${rastro.arquivos.cookie-name:}") String nomeDoCookie, Clock relogio,
            @Value("${rastro.conexoes.teste-timeout-segundos:15}") int tempoLimiteSegundos) {
        this(url, FonteDoToken.de(token, arquivoDoToken), modo, nomeDoCookie, relogio,
                Duration.ofSeconds(Math.max(1, tempoLimiteSegundos)), duracao -> Thread.sleep(duracao.toMillis()));
    }

    VerificadorDaApiDeArquivos(String url, FonteDoToken token, String modo, String nomeDoCookie, Clock relogio,
            Duration tempoLimite, Espera espera) {
        this.acesso = new AcessoAApiDeArquivos(url, modo, nomeDoCookie);
        this.token = token;
        this.relogio = relogio;
        this.tempoLimite = tempoLimite;
        this.espera = espera;
    }

    @Override
    public String nome() {
        return "arquivos";
    }

    @Override
    public String rotulo() {
        return "API de arquivos do Licenciamento";
    }

    @Override
    public Estado estado() {
        return acesso.configurado() ? Estado.CONFIGURADA : Estado.NAO_CONFIGURADA;
    }

    @Override
    public Optional<Long> horasAteExpirar() {
        if (estado() != Estado.CONFIGURADA) {
            return Optional.empty();
        }
        try {
            return ExpiracaoDoToken.de(token.obter()).map(this::horasAte);
        } catch (FalhaNoDocumentoException e) {
            return Optional.empty();
        }
    }

    private long horasAte(Instant expiracao) {
        return Duration.between(relogio.instant(), expiracao).toHours();
    }

    @Override
    public Resultado verificar() {
        String valor;
        try {
            valor = token.obter();
        } catch (FalhaNoDocumentoException e) {
            return Resultado.falha("Token não configurado.");
        }
        if (ExpiracaoDoToken.de(valor).filter(expiracao -> !expiracao.isAfter(relogio.instant())).isPresent()) {
            return new Resultado(Classificacao.FALHA_DE_AUTENTICACAO,
                    "Token vencido: capture um novo token, atualize o arquivo e teste de novo.", null);
        }
        try {
            int status = chamar(valor);
            if (status == 503) {
                log.info("Teste da API de arquivos: HTTP 503; nova tentativa");
                espera.aguardar(ESPERA_DA_NOVA_TENTATIVA);
                status = chamar(valor);
                if (status == 503) {
                    return Resultado.indisponivelPersistente(status);
                }
            }
            return Resultado.deHttp(status, "uma chave fictícia", "token");
        } catch (ResourceAccessException e) {
            return Resultado.deRede(e, (int) tempoLimite.toSeconds());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultado.falha("Teste interrompido.");
        } catch (RuntimeException e) {
            log.warn("Teste da API de arquivos falhou: {}", e.getClass().getSimpleName());
            return Resultado.falha("Não foi possível testar a conexão.");
        }
    }

    private int chamar(String valor) {
        return AcessoAApiDeArquivos.cliente(tempoLimite, tempoLimite).get().uri(acesso.arquivo(CHAVE_FICTICIA))
                .headers(h -> acesso.autenticar(h, valor))
                .exchange((requisicao, resposta) -> resposta.getStatusCode().value(), true);
    }
}
