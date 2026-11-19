package br.gov.mt.sema.rastro.integration.licenciamento;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Teste da API de arquivos: um {@code GET} numa chave que não existe, sem ler corpo. 404 prova que o serviço responde e
 * aceitou o token; 401 e 403, que recusou. A API não tem rota de saúde conhecida (R55). Token vencido nem é enviado.
 */
@Component
@Order(2)
class VerificadorDaApiDeArquivos implements VerificadorDeConexao {

    static final String CAMINHO = "/api/arquivo/verificacao-de-conexao";
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(5);

    private final String urlBase;
    private final FonteDoToken token;
    private final boolean modoCookie;
    private final String nomeDoCookie;
    private final Clock relogio;

    @Autowired
    VerificadorDaApiDeArquivos(@Value("${rastro.arquivos.url:}") String url,
            @Value("${rastro.arquivos.token:}") String token,
            @Value("${rastro.arquivos.token-file:}") String arquivoDoToken,
            @Value("${rastro.arquivos.auth-mode:bearer}") String modo,
            @Value("${rastro.arquivos.cookie-name:}") String nomeDoCookie, Clock relogio) {
        this(url, FonteDoToken.de(token, arquivoDoToken), modo, nomeDoCookie, relogio);
    }

    VerificadorDaApiDeArquivos(String url, FonteDoToken token, String modo, String nomeDoCookie, Clock relogio) {
        this.urlBase = url == null ? "" : url.trim().replaceAll("/+$", "");
        this.token = token;
        this.modoCookie = "cookie".equalsIgnoreCase(modo == null ? "" : modo.trim());
        this.nomeDoCookie = nomeDoCookie == null ? "" : nomeDoCookie.trim();
        this.relogio = relogio;
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
        return urlBase.isEmpty() ? Estado.NAO_CONFIGURADA : Estado.CONFIGURADA;
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
            return new Resultado(false, "Token não configurado.");
        }
        if (ExpiracaoDoToken.de(valor).filter(expiracao -> !expiracao.isAfter(relogio.instant())).isPresent()) {
            return new Resultado(false, "Token vencido.");
        }
        try {
            return cliente().get().uri(URI.create(urlBase + CAMINHO)).headers(h -> autenticar(h, valor))
                    .exchange((requisicao, resposta) -> interpretar(resposta.getStatusCode().value()), true);
        } catch (ResourceAccessException e) {
            return new Resultado(false, porTempoLimite(e) ? "Tempo limite excedido." : "Serviço inacessível.");
        } catch (RuntimeException e) {
            return new Resultado(false, "Não foi possível testar a conexão.");
        }
    }

    private static Resultado interpretar(int status) {
        if (status == 401 || status == 403) {
            return new Resultado(false, "Token recusado.");
        }
        if (status == 404 || (status >= 200 && status < 300)) {
            return new Resultado(true, "Conexão estabelecida.");
        }
        return new Resultado(false, "Serviço respondeu HTTP " + status + ".");
    }

    private RestClient cliente() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) TEMPO_LIMITE.toMillis());
        fabrica.setReadTimeout((int) TEMPO_LIMITE.toMillis());
        return RestClient.builder().requestFactory(fabrica).build();
    }

    private void autenticar(HttpHeaders cabecalhos, String valor) {
        if (valor.isEmpty()) {
            return;
        }
        if (modoCookie) {
            cabecalhos.set(HttpHeaders.COOKIE, nomeDoCookie + "=" + valor);
        } else {
            cabecalhos.setBearerAuth(valor);
        }
    }

    private static boolean porTempoLimite(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
