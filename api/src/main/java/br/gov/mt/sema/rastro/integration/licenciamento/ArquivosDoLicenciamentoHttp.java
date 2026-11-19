package br.gov.mt.sema.rastro.integration.licenciamento;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import br.gov.mt.sema.rastro.documento.domain.SituacaoDocumento;
import br.gov.mt.sema.rastro.dossie.application.port.ArquivosDoRepositorio;
import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.rastro.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.rastro.shared.application.exception.ArmazenamentoInsuficienteException;
import br.gov.mt.sema.rastro.shared.application.exception.CredencialArquivosRecusadaException;
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
 * ou em {@code Cookie: <nome>=<token>} (modo cookie); vem de {@link FonteDoToken} (variável ou arquivo relido a cada
 * tentativa). Nenhuma mensagem, log ou exceção traz URL, token ou chave.
 *
 * <p>Tempo (S2.2): conexão em 10 s; inatividade da leitura em {@code DOWNLOAD_TIMEOUT_SEGUNDOS} (vale também no meio
 * do corpo); teto total por documento em {@code DOWNLOAD_TOTAL_MAXIMO_SEGUNDOS}, somando as novas tentativas.
 *
 * <ul>
 *   <li>401/403: {@link CredencialArquivosRecusadaException}, interrompe toda a geração, sem nova tentativa;</li>
 *   <li>404: falha do documento, sem nova tentativa;</li>
 *   <li>tempo limite de inatividade, 503 e queda de conexão: até 2 novas tentativas (espera de 2 s e 5 s);</li>
 *   <li>teto total, outros 5xx e demais status: falha do documento, sem nova tentativa.</li>
 * </ul>
 */
@Component
@ConditionalOnExpression(ArquivosCondicoes.CONFIGURADO)
class ArquivosDoLicenciamentoHttp implements ArquivosDoRepositorio {

    private static final Logger log = LoggerFactory.getLogger(ArquivosDoLicenciamentoHttp.class);

    /** Espera antes de cada nova tentativa; o tamanho da lista é o número de novas tentativas. */
    static final List<Duration> ESPERAS = List.of(Duration.ofSeconds(2), Duration.ofSeconds(5));

    static final String TEMPO_LIMITE = "tempo limite excedido ao obter o arquivo";
    static final String TETO_TOTAL = "tempo total máximo excedido ao obter o arquivo";
    static final String INDISPONIVEL = "repositório de arquivos indisponível";
    static final String NAO_ENCONTRADO = "arquivo não encontrado no repositório";

    /** Pausa entre tentativas; nos testes, não espera de verdade. */
    interface Espera {
        void aguardar(Duration duracao) throws InterruptedException;
    }

    private final String urlBase;
    private final FonteDoToken token;
    private final boolean modoCookie;
    private final String nomeDoCookie;
    private final Supplier<LimitesDeDownload> limites;
    private final Espera espera;

    @Autowired
    ArquivosDoLicenciamentoHttp(@Value("${rastro.arquivos.url}") String url,
            @Value("${rastro.arquivos.token:}") String token,
            @Value("${rastro.arquivos.token-file:}") String arquivoDoToken,
            @Value("${rastro.arquivos.auth-mode:bearer}") String modo,
            @Value("${rastro.arquivos.cookie-name:}") String nomeDoCookie, ParametrosDoDossie parametros) {
        this(url, FonteDoToken.de(token, arquivoDoToken), modo, nomeDoCookie,
                () -> new LimitesDeDownload(LimitesDeDownload.CONEXAO_PADRAO,
                        Duration.ofSeconds(Math.max(1, parametros.downloadTimeoutSegundos())),
                        Duration.ofSeconds(Math.max(1, parametros.downloadTotalMaximoSegundos()))),
                duracao -> Thread.sleep(duracao.toMillis()));
    }

    ArquivosDoLicenciamentoHttp(String url, String token, String modo, String nomeDoCookie,
            Supplier<LimitesDeDownload> limites, Espera espera) {
        this(url, FonteDoToken.de(token, null), modo, nomeDoCookie, limites, espera);
    }

    ArquivosDoLicenciamentoHttp(String url, FonteDoToken token, String modo, String nomeDoCookie,
            Supplier<LimitesDeDownload> limites, Espera espera) {
        String semBarraFinal = url.trim();
        while (semBarraFinal.endsWith("/")) {
            semBarraFinal = semBarraFinal.substring(0, semBarraFinal.length() - 1);
        }
        this.urlBase = semBarraFinal;
        this.token = token;
        this.modoCookie = "cookie".equalsIgnoreCase(modo == null ? "" : modo.trim());
        this.nomeDoCookie = nomeDoCookie == null ? "" : nomeDoCookie.trim();
        this.limites = limites;
        this.espera = espera;
    }

    @Override
    public int baixar(String chave, Path destino) {
        if (!SituacaoDocumento.chaveValida(chave)) {
            // A chave "0" nunca é requisitada (docs/05).
            throw new FalhaNoDocumentoException("arquivo indisponível no repositório", 0);
        }
        // O segmento é codificado inteiro (inclusive "/"), para uma chave nunca alterar o caminho da requisição.
        URI uri = URI.create(urlBase + "/api/arquivo/" + UriUtils.encodePathSegment(chave.trim(), StandardCharsets.UTF_8));
        LimitesDeDownload limite = limites.get();
        long prazo = System.nanoTime() + limite.totalMaximo().toNanos();
        int tentativa = 0;
        while (true) {
            tentativa++;
            // Relido a cada tentativa: um token novo no arquivo vale já na próxima (bloco 4, S2.2).
            String valorDoToken;
            try {
                valorDoToken = token.obter();
            } catch (FalhaNoDocumentoException e) {
                throw new FalhaNoDocumentoException(e.getMessage(), tentativa - 1);
            }
            boolean concluido = false;
            try {
                tentar(uri, destino, limite, prazo, valorDoToken);
                concluido = true;
                return tentativa;
            } catch (FalhaTemporaria e) {
                Duration pausa = tentativa <= ESPERAS.size() ? ESPERAS.get(tentativa - 1) : null;
                if (pausa == null || System.nanoTime() + pausa.toNanos() - prazo >= 0) {
                    throw new FalhaNoDocumentoException(e.getMessage(), tentativa);
                }
                log.info("API de arquivos: {}; nova tentativa ({} de {}) em {} s", e.getMessage(), tentativa + 1,
                        ESPERAS.size() + 1, pausa.toSeconds());
                aguardar(pausa, tentativa);
            } catch (FalhaNoDocumentoException e) {
                throw new FalhaNoDocumentoException(e.getMessage(), tentativa);
            } finally {
                if (!concluido) {
                    apagarParcial(destino);
                }
            }
        }
    }

    private void tentar(URI uri, Path destino, LimitesDeDownload limite, long prazo, String valorDoToken) {
        try {
            cliente(limite).get().uri(uri).headers(h -> autenticar(h, valorDoToken)).exchange((requisicao, resposta) -> {
                int status = resposta.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw new CredencialArquivosRecusadaException();
                }
                if (status == 404) {
                    throw new FalhaNoDocumentoException(NAO_ENCONTRADO);
                }
                if (status == 503) {
                    throw new FalhaTemporaria(INDISPONIVEL);
                }
                if (status >= 500) {
                    throw new FalhaNoDocumentoException(INDISPONIVEL);
                }
                if (status < 200 || status >= 300) {
                    log.warn("API de arquivos respondeu HTTP {}", status);
                    throw new FalhaNoDocumentoException("o repositório de arquivos recusou o pedido (HTTP " + status + ")");
                }
                try (InputStream corpo = resposta.getBody()) {
                    copiar(corpo, destino, prazo);
                }
                return null;
            });
        } catch (ResourceAccessException e) {
            // Tempo de inatividade estourado ou conexão recusada/interrompida: vale nova tentativa.
            throw new FalhaTemporaria(porTempoLimite(e) ? TEMPO_LIMITE : INDISPONIVEL);
        } catch (RestClientException e) {
            log.warn("Falha ao chamar a API de arquivos: {}", e.getClass().getSimpleName());
            throw new FalhaNoDocumentoException(INDISPONIVEL);
        }
    }

    /** Copia em blocos, sem guardar o arquivo em memória; confere o teto total a cada bloco. */
    private static void copiar(InputStream corpo, Path destino, long prazo) throws IOException {
        OutputStream saida;
        try {
            saida = Files.newOutputStream(destino);
        } catch (IOException e) {
            throw naoGravou(e);
        }
        try {
            try (saida) {
                byte[] bloco = new byte[64 * 1024];
                int lidos;
                while ((lidos = corpo.read(bloco)) >= 0) {
                    try {
                        saida.write(bloco, 0, lidos);
                    } catch (IOException e) {
                        throw naoGravou(e);
                    }
                    if (System.nanoTime() - prazo > 0) {
                        throw new FalhaNoDocumentoException(TETO_TOTAL);
                    }
                }
            }
        } catch (IOException e) {
            // O fechamento também grava (o resto do buffer): sem espaço ele falha aqui.
            if (ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(e)) {
                throw new ArmazenamentoInsuficienteException();
            }
            throw e;
        }
    }

    /**
     * Falha ao gravar o temporário. Sem espaço em disco (S6, bloco 3) não é defeito do documento: sai como
     * {@link ArmazenamentoInsuficienteException}, sem o caminho nem a mensagem crua do sistema.
     */
    private static RuntimeException naoGravou(IOException e) {
        if (ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(e)) {
            return new ArmazenamentoInsuficienteException();
        }
        return new FalhaNoDocumentoException("não foi possível gravar o arquivo temporário");
    }

    private void aguardar(Duration pausa, int tentativa) {
        try {
            espera.aguardar(pausa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaNoDocumentoException("obtenção do arquivo interrompida", tentativa);
        }
    }

    private RestClient cliente(LimitesDeDownload limite) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(milissegundos(limite.conexao()));
        // Tempo máximo sem receber bytes, vale também para o corpo (não só até o cabeçalho).
        fabrica.setReadTimeout(milissegundos(limite.inatividade()));
        return RestClient.builder().requestFactory(fabrica).build();
    }

    private static int milissegundos(Duration duracao) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, duracao.toMillis()));
    }

    private void autenticar(HttpHeaders cabecalhos, String valorDoToken) {
        if (valorDoToken.isEmpty()) {
            return;
        }
        if (modoCookie) {
            cabecalhos.set(HttpHeaders.COOKIE, nomeDoCookie + "=" + valorDoToken);
        } else {
            cabecalhos.setBearerAuth(valorDoToken);
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

    /** Falha que vale nova tentativa (tempo de inatividade, 503, queda de conexão). */
    private static final class FalhaTemporaria extends RuntimeException {
        FalhaTemporaria(String motivo) {
            super(motivo, null, false, false);
        }
    }
}
