package br.gov.mt.sema.rastro.integration.scorp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

/**
 * Montagem única do acesso ao SCORP (S6, bloco 4): base da URL ({@code SCORP_API_URL}, que termina em {@code /api}),
 * caminho dos documentos da pessoa, autenticação e cliente HTTP. A consulta real ({@link DocumentosDoScorpHttp}) e o
 * teste de conexão ({@link VerificadorDoScorp}) usam esta mesma classe: se a URL do fluxo real mudar, o teste muda
 * junto. Nada daqui vai para log, resposta ou mensagem.
 */
final class AcessoAoScorp {

    static final String CAMINHO = "/documentos/pessoa-licenciamento/";

    private final String urlBase;
    private final boolean modoCookie;
    private final String nomeDoCookie;

    AcessoAoScorp(String url, String modo, String nomeDoCookie) {
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

    /** {@code {SCORP_API_URL}/documentos/pessoa-licenciamento/{idCorporativo}}, com o segmento codificado. */
    URI documentosDaPessoa(String idCorporativo) {
        return URI.create(urlBase + CAMINHO + UriUtils.encodePathSegment(idCorporativo.trim(), StandardCharsets.UTF_8));
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

    static RestClient cliente(Duration tempoLimite) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        int milissegundos = (int) Math.max(1, Math.min(Integer.MAX_VALUE, tempoLimite.toMillis()));
        fabrica.setConnectTimeout(milissegundos);
        fabrica.setReadTimeout(milissegundos);
        return RestClient.builder().requestFactory(fabrica).build();
    }
}
