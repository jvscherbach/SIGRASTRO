package br.gov.mt.sema.rastro.integration.licenciamento;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

/**
 * Montagem única do acesso à API de arquivos (S6, bloco 4): base da URL, caminho do arquivo, autenticação (bearer ou
 * cookie) e o cliente HTTP. O download real ({@link ArquivosDoLicenciamentoHttp}) e o teste de conexão
 * ({@link VerificadorDaApiDeArquivos}) usam esta mesma classe: se a URL do fluxo real mudar, o teste muda junto. Nada
 * daqui vai para log, resposta ou mensagem.
 */
final class AcessoAApiDeArquivos {

    static final String CAMINHO_DO_ARQUIVO = "/api/arquivo/";

    private final String urlBase;
    private final boolean modoCookie;
    private final String nomeDoCookie;

    AcessoAApiDeArquivos(String url, String modo, String nomeDoCookie) {
        String semBarraFinal = url == null ? "" : url.trim();
        while (semBarraFinal.endsWith("/")) {
            semBarraFinal = semBarraFinal.substring(0, semBarraFinal.length() - 1);
        }
        this.urlBase = semBarraFinal;
        this.modoCookie = "cookie".equalsIgnoreCase(modo == null ? "" : modo.trim());
        this.nomeDoCookie = nomeDoCookie == null ? "" : nomeDoCookie.trim();
    }

    boolean configurado() {
        return !urlBase.isEmpty();
    }

    /** {@code {url}/api/arquivo/{chave}}; o segmento é codificado inteiro (inclusive "/"). */
    URI arquivo(String chave) {
        return URI.create(urlBase + CAMINHO_DO_ARQUIVO + UriUtils.encodePathSegment(chave.trim(), StandardCharsets.UTF_8));
    }

    void autenticar(HttpHeaders cabecalhos, String valorDoToken) {
        if (valorDoToken == null || valorDoToken.isEmpty()) {
            return;
        }
        if (modoCookie) {
            cabecalhos.set(HttpHeaders.COOKIE, nomeDoCookie + "=" + valorDoToken);
        } else {
            cabecalhos.setBearerAuth(valorDoToken);
        }
    }

    /** O mesmo tipo de cliente do download: conexão e leitura (inatividade) limitadas. */
    static RestClient cliente(Duration conexao, Duration leitura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(milissegundos(conexao));
        fabrica.setReadTimeout(milissegundos(leitura));
        return RestClient.builder().requestFactory(fabrica).build();
    }

    private static int milissegundos(Duration duracao) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, duracao.toMillis()));
    }
}
