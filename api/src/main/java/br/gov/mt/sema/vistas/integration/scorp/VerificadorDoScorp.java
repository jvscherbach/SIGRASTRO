package br.gov.mt.sema.vistas.integration.scorp;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;

import br.gov.mt.sema.vistas.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.vistas.documento.application.port.OrigensLigadas;
import br.gov.mt.sema.vistas.documento.domain.OrigemDocumento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Teste do SCORP: um {@code GET} para uma pessoa que não existe, sem ler corpo. Uma resposta 2xx ou 404 prova que o serviço
 * responde e aceitou a credencial; 401 e 403, que recusou. Não há rota de saúde conhecida (R55).
 */
@Component
@Order(3)
class VerificadorDoScorp implements VerificadorDeConexao {

    static final String IDENTIFICADOR = "verificacao-de-conexao";
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(5);

    private final String urlBase;
    private final OrigensLigadas origens;
    private final CredencialDoScorp credencial;
    private final boolean modoCookie;
    private final String nomeDoCookie;

    @Autowired
    VerificadorDoScorp(@Value("${vistas.scorp.url:}") String url, OrigensLigadas origens,
            @Value("${vistas.scorp.token-file:}") String arquivoDoToken,
            @Value("${vistas.arquivos.token-file:}") String arquivoDoTokenDoLicenciamento,
            @Value("${vistas.arquivos.token:}") String token,
            @Value("${vistas.scorp.auth-mode:}") String modo,
            @Value("${vistas.arquivos.auth-mode:bearer}") String modoDoLicenciamento,
            @Value("${vistas.scorp.cookie-name:}") String nomeDoCookie,
            @Value("${vistas.arquivos.cookie-name:}") String nomeDoCookieDoLicenciamento) {
        this(url, origens, CredencialDoScorp.de(token, arquivoDoToken, arquivoDoTokenDoLicenciamento),
                modo == null || modo.isBlank() ? modoDoLicenciamento : modo,
                nomeDoCookie == null || nomeDoCookie.isBlank() ? nomeDoCookieDoLicenciamento : nomeDoCookie);
    }

    VerificadorDoScorp(String url, OrigensLigadas origens, CredencialDoScorp credencial, String modo, String nomeDoCookie) {
        this.urlBase = url == null ? "" : url.trim().replaceAll("/+$", "");
        this.origens = origens;
        this.credencial = credencial;
        this.modoCookie = "cookie".equalsIgnoreCase(modo == null ? "" : modo.trim());
        this.nomeDoCookie = nomeDoCookie == null ? "" : nomeDoCookie.trim();
    }

    @Override
    public String nome() {
        return "scorp";
    }

    @Override
    public String rotulo() {
        return "SCORP";
    }

    @Override
    public Estado estado() {
        if (urlBase.isEmpty()) {
            return Estado.NAO_CONFIGURADA;
        }
        return origens.ligada(OrigemDocumento.SCORP) ? Estado.CONFIGURADA : Estado.DESATIVADA;
    }

    @Override
    public Resultado verificar() {
        String valor;
        try {
            valor = credencial.obter();
        } catch (FalhaDoScorp e) {
            return new Resultado(false, "Credencial não configurada.");
        }
        try {
            return cliente().get().uri(URI.create(urlBase + DocumentosDoScorpHttp.CAMINHO + IDENTIFICADOR))
                    .headers(h -> autenticar(h, valor))
                    .exchange((requisicao, resposta) -> interpretar(resposta.getStatusCode().value()), true);
        } catch (ResourceAccessException e) {
            return new Resultado(false, porTempoLimite(e) ? "Tempo limite excedido." : "Serviço inacessível.");
        } catch (RuntimeException e) {
            return new Resultado(false, "Não foi possível testar a conexão.");
        }
    }

    private static Resultado interpretar(int status) {
        if (status == 401 || status == 403) {
            return new Resultado(false, "Credencial recusada.");
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
