package br.gov.mt.sema.vistas.integration.licenciamento;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.Supplier;

import br.gov.mt.sema.vistas.documento.domain.SituacaoDocumento;
import br.gov.mt.sema.vistas.dossie.application.port.ArquivosDoRepositorio;
import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.shared.application.exception.CredencialArquivosRecusadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

/**
 * Cliente da API de arquivos do Licenciamento: {@code GET {url}/api/arquivo/{chave}} (docs/05). O corpo vai direto
 * para o arquivo temporário (RNF03), nunca para a memória. O token vai em {@code Authorization: Bearer} (modo bearer)
 * ou em {@code Cookie: <nome>=<token>} (modo cookie). Nenhuma mensagem, log ou exceção traz URL, token ou chave.
 *
 * <ul>
 *   <li>401/403: {@link CredencialArquivosRecusadaException}, interrompe toda a geração;</li>
 *   <li>404, 503, tempo limite e demais falhas: {@link FalhaNoDocumentoException}, falha só daquele documento.</li>
 * </ul>
 */
@Component
@ConditionalOnExpression(ArquivosCondicoes.CONFIGURADO)
class ArquivosDoLicenciamentoHttp implements ArquivosDoRepositorio {

    private static final Logger log = LoggerFactory.getLogger(ArquivosDoLicenciamentoHttp.class);

    private final String urlBase;
    private final String token;
    private final boolean modoCookie;
    private final String nomeDoCookie;
    private final Supplier<Duration> limiteDeTempo;

    @Autowired
    ArquivosDoLicenciamentoHttp(@Value("${vistas.arquivos.url}") String url,
            @Value("${vistas.arquivos.token:}") String token,
            @Value("${vistas.arquivos.auth-mode:bearer}") String modo,
            @Value("${vistas.arquivos.cookie-name:}") String nomeDoCookie, ParametrosDoDossie parametros) {
        this(url, token, modo, nomeDoCookie, () -> Duration.ofSeconds(Math.max(1, parametros.downloadTimeoutSegundos())));
    }

    ArquivosDoLicenciamentoHttp(String url, String token, String modo, String nomeDoCookie,
            Supplier<Duration> limiteDeTempo) {
        String semBarraFinal = url.trim();
        while (semBarraFinal.endsWith("/")) {
            semBarraFinal = semBarraFinal.substring(0, semBarraFinal.length() - 1);
        }
        this.urlBase = semBarraFinal;
        this.token = token == null ? "" : token.trim();
        this.modoCookie = "cookie".equalsIgnoreCase(modo == null ? "" : modo.trim());
        this.nomeDoCookie = nomeDoCookie == null ? "" : nomeDoCookie.trim();
        this.limiteDeTempo = limiteDeTempo;
    }

    @Override
    public void baixar(String chave, Path destino) {
        if (!SituacaoDocumento.chaveValida(chave)) {
            // A chave "0" nunca é requisitada (docs/05).
            throw new FalhaNoDocumentoException("arquivo indisponível no repositório");
        }
        // O segmento é codificado inteiro (inclusive "/"), para uma chave nunca alterar o caminho da requisição.
        URI uri = URI.create(urlBase + "/api/arquivo/" + UriUtils.encodePathSegment(chave.trim(), StandardCharsets.UTF_8));
        boolean concluido = false;
        try {
            cliente().get().uri(uri).headers(this::autenticar).exchange((requisicao, resposta) -> {
                int status = resposta.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw new CredencialArquivosRecusadaException();
                }
                if (status == 404) {
                    throw new FalhaNoDocumentoException("arquivo não encontrado no repositório");
                }
                if (status >= 500) {
                    throw new FalhaNoDocumentoException("repositório de arquivos indisponível");
                }
                if (status < 200 || status >= 300) {
                    log.warn("API de arquivos respondeu HTTP {}", status);
                    throw new FalhaNoDocumentoException("o repositório de arquivos recusou o pedido (HTTP " + status + ")");
                }
                try (InputStream corpo = resposta.getBody()) {
                    Files.copy(corpo, destino, StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            });
            concluido = true;
        } catch (ResourceAccessException e) {
            throw new FalhaNoDocumentoException(porTempoLimite(e)
                    ? "tempo limite excedido ao obter o arquivo" : "repositório de arquivos indisponível");
        } catch (RestClientException e) {
            log.warn("Falha ao chamar a API de arquivos: {}", e.getClass().getSimpleName());
            throw new FalhaNoDocumentoException("repositório de arquivos indisponível");
        } finally {
            if (!concluido) {
                apagarParcial(destino);
            }
        }
    }

    private RestClient cliente() {
        int milissegundos = (int) Math.min(Integer.MAX_VALUE, limiteDeTempo.get().toMillis());
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(milissegundos);
        // Tempo máximo sem receber bytes, vale também para o corpo (não só até o cabeçalho).
        fabrica.setReadTimeout(milissegundos);
        return RestClient.builder().requestFactory(fabrica).build();
    }

    private void autenticar(HttpHeaders cabecalhos) {
        if (token.isEmpty()) {
            return;
        }
        if (modoCookie) {
            cabecalhos.set(HttpHeaders.COOKIE, nomeDoCookie + "=" + token);
        } else {
            cabecalhos.setBearerAuth(token);
        }
    }

    private static boolean porTempoLimite(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException || causa instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static void apagarParcial(Path destino) {
        try {
            Files.deleteIfExists(destino);
        } catch (IOException e) {
            log.warn("Não foi possível apagar o arquivo parcial: {}", e.getMessage());
        }
    }
}
