package br.gov.mt.sema.rastro.integration.scorp;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

import br.gov.mt.sema.rastro.documento.application.port.DocumentosPessoais;
import br.gov.mt.sema.rastro.documento.application.port.OrigensLigadas;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Cliente do SCORP (S4, docs/05): {@code GET {SCORP_API_URL}/documentos/pessoa-licenciamento/{idCorporativo}}, que
 * devolve uma lista com {@code chave}, {@code nomeArquivo}, {@code descricao}, {@code dataInicio} e {@code dataFim}.
 * Único lugar que fala com o SCORP.
 *
 * <ul>
 *   <li>Desligado sem {@code SCORP_API_URL} ou com {@code rastro.documentos.origens.scorp=false}: nenhuma chamada.</li>
 *   <li>Autenticação NÃO CONFIRMADA (P06): bearer ou cookie, como a API de arquivos ({@link CredencialDoScorp}).</li>
 *   <li>Conexão e leitura limitadas a {@code SCORP_TIMEOUT_SEGUNDOS}; no máximo 1 nova tentativa (tempo limite,
 *       queda de conexão, 5xx); 401, 403, 404, outros status e resposta inválida falham sem nova tentativa.</li>
 *   <li>Chamadas simultâneas limitadas a {@code SCORP_CHAMADAS_SIMULTANEAS} em todo o serviço.</li>
 *   <li>Nunca lança por falha do SCORP: a pessoa vai para {@code falharam}. Log só com o motivo: sem URL, token,
 *       cookie, caminho do arquivo do token, chave nem identificador da pessoa.</li>
 * </ul>
 */
@Component
class DocumentosDoScorpHttp implements DocumentosPessoais {

    private static final Logger log = LoggerFactory.getLogger(DocumentosDoScorpHttp.class);

    static final String CAMINHO = AcessoAoScorp.CAMINHO;
    /** Resposta acima disto é tratada como inválida (o SCORP devolve só metadados). */
    static final int MAXIMO_DE_BYTES = 5 * 1024 * 1024;
    static final Duration ESPERA_DA_NOVA_TENTATIVA = Duration.ofSeconds(1);
    private static final ZoneId FUSO = ZoneId.of("America/Cuiaba");
    private static final DateTimeFormatter BR_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm[:ss]");
    private static final DateTimeFormatter BR_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Pausa antes da nova tentativa; nos testes, não espera de verdade. */
    interface Espera {
        void aguardar(Duration duracao) throws InterruptedException;
    }

    private final AcessoAoScorp acesso;
    private final BooleanSupplier origemLigada;
    private final CredencialDoScorp credencial;
    private final Duration tempoLimite;
    private final Semaphore vagas;
    private final ObjectMapper json;
    private final Espera espera;

    @Autowired
    DocumentosDoScorpHttp(@Value("${rastro.scorp.url:}") String url,
            OrigensLigadas origens,
            @Value("${rastro.scorp.token-file:}") String arquivoDoToken,
            @Value("${rastro.arquivos.token-file:}") String arquivoDoTokenDoLicenciamento,
            @Value("${rastro.arquivos.token:}") String token,
            @Value("${rastro.scorp.auth-mode:}") String modo,
            @Value("${rastro.arquivos.auth-mode:bearer}") String modoDoLicenciamento,
            @Value("${rastro.scorp.cookie-name:}") String nomeDoCookie,
            @Value("${rastro.arquivos.cookie-name:}") String nomeDoCookieDoLicenciamento,
            @Value("${rastro.scorp.timeout-segundos:15}") int timeoutSegundos,
            @Value("${rastro.scorp.chamadas-simultaneas:4}") int chamadasSimultaneas, ObjectMapper json) {
        this(url, () -> origens.ligada(OrigemDocumento.SCORP), CredencialDoScorp.de(token, arquivoDoToken, arquivoDoTokenDoLicenciamento),
                vazio(modo) ? modoDoLicenciamento : modo, vazio(nomeDoCookie) ? nomeDoCookieDoLicenciamento : nomeDoCookie,
                Duration.ofSeconds(Math.max(1, timeoutSegundos)), chamadasSimultaneas, json,
                duracao -> Thread.sleep(duracao.toMillis()));
    }

    DocumentosDoScorpHttp(String url, boolean origemLigada, CredencialDoScorp credencial, String modo,
            String nomeDoCookie, Duration tempoLimite, int chamadasSimultaneas, ObjectMapper json, Espera espera) {
        this(url, () -> origemLigada, credencial, modo, nomeDoCookie, tempoLimite, chamadasSimultaneas, json, espera);
    }

    private DocumentosDoScorpHttp(String url, BooleanSupplier origemLigada, CredencialDoScorp credencial, String modo,
            String nomeDoCookie, Duration tempoLimite, int chamadasSimultaneas, ObjectMapper json, Espera espera) {
        this.acesso = new AcessoAoScorp(url, modo, nomeDoCookie);
        this.origemLigada = origemLigada;
        this.credencial = credencial;
        this.tempoLimite = tempoLimite;
        this.vagas = new Semaphore(Math.max(1, chamadasSimultaneas), true);
        this.json = json;
        this.espera = espera;
    }

    @Override
    public boolean habilitado() {
        return origemLigada.getAsBoolean() && acesso.configurado();
    }

    @Override
    public Consulta consultar(Set<String> idsCorporativos) {
        if (!habilitado() || idsCorporativos.isEmpty()) {
            return new Consulta(Map.of(), Set.of());
        }
        Map<String, Future<List<DocumentoPessoal>>> futuros = new LinkedHashMap<>();
        Map<String, List<DocumentoPessoal>> documentos = new LinkedHashMap<>();
        Set<String> falharam = new HashSet<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String id : idsCorporativos) {
                futuros.put(id, executor.submit(() -> comVaga(id)));
            }
            for (Map.Entry<String, Future<List<DocumentoPessoal>>> futuro : futuros.entrySet()) {
                try {
                    documentos.put(futuro.getKey(), futuro.getValue().get());
                } catch (ExecutionException e) {
                    falharam.add(futuro.getKey());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    falharam.add(futuro.getKey());
                }
            }
        }
        return new Consulta(documentos, falharam);
    }

    private List<DocumentoPessoal> comVaga(String idCorporativo) throws InterruptedException {
        vagas.acquire();
        try {
            return consultarPessoa(idCorporativo);
        } finally {
            vagas.release();
        }
    }

    private List<DocumentoPessoal> consultarPessoa(String idCorporativo) throws InterruptedException {
        URI uri = acesso.documentosDaPessoa(idCorporativo);
        for (int tentativa = 1; ; tentativa++) {
            try {
                return chamar(uri);
            } catch (FalhaDoScorp e) {
                if (!e.temporaria() || tentativa >= 2) {
                    log.warn("SCORP: consulta de documentos pessoais falhou ({})", e.getMessage());
                    throw e;
                }
                log.info("SCORP: {}; nova tentativa", e.getMessage());
                espera.aguardar(ESPERA_DA_NOVA_TENTATIVA);
            }
        }
    }

    private List<DocumentoPessoal> chamar(URI uri) {
        String valorDoToken = credencial.obter();
        try {
            return AcessoAoScorp.cliente(tempoLimite).get().uri(uri).headers(h -> acesso.autenticar(h, valorDoToken))
                    .exchange((requisicao, resposta) -> {
                int status = resposta.getStatusCode().value();
                if (status >= 500) {
                    throw new FalhaDoScorp("HTTP " + status, true);
                }
                if (status < 200 || status >= 300) {
                    throw new FalhaDoScorp("HTTP " + status);
                }
                try (InputStream corpo = resposta.getBody()) {
                    return interpretar(ler(corpo));
                }
            });
        } catch (FalhaDoScorp e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new FalhaDoScorp(porTempoLimite(e) ? "tempo limite" : "SCORP inacessível", true);
        } catch (RestClientException e) {
            throw new FalhaDoScorp("resposta inesperada (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static byte[] ler(InputStream corpo) throws IOException {
        byte[] bytes = corpo.readNBytes(MAXIMO_DE_BYTES + 1);
        if (bytes.length > MAXIMO_DE_BYTES) {
            throw new FalhaDoScorp("resposta grande demais");
        }
        return bytes;
    }

    /** Lista de objetos; item sem campo vira nulo; data em formato desconhecido vira nula (documento sem data). */
    private List<DocumentoPessoal> interpretar(byte[] corpo) {
        JsonNode raiz;
        try {
            raiz = json.readTree(corpo);
        } catch (IOException e) {
            throw new FalhaDoScorp("resposta inválida");
        }
        if (raiz == null || !raiz.isArray()) {
            throw new FalhaDoScorp("resposta inválida");
        }
        List<DocumentoPessoal> itens = new ArrayList<>();
        for (JsonNode item : raiz) {
            if (!item.isObject()) {
                throw new FalhaDoScorp("resposta inválida");
            }
            itens.add(new DocumentoPessoal(texto(item, "chave"), texto(item, "nomeArquivo"), texto(item, "descricao"),
                    data(item.get("dataInicio")), data(item.get("dataFim"))));
        }
        return itens;
    }

    private static String texto(JsonNode item, String campo) {
        JsonNode valor = item.get(campo);
        if (valor == null || valor.isNull() || valor.isContainerNode()) {
            return null;
        }
        return valor.asText();
    }

    /** Formato da data NÃO CONFIRMADO: aceita ISO (com ou sem hora e fuso), dd/MM/yyyy e epoch em milissegundos. */
    static LocalDateTime data(JsonNode valor) {
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (valor.isNumber()) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(valor.asLong()), FUSO);
        }
        String texto = valor.asText().trim();
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(texto);
        } catch (DateTimeParseException ignorada) {
            // próximo formato
        }
        try {
            return OffsetDateTime.parse(texto).atZoneSameInstant(FUSO).toLocalDateTime();
        } catch (DateTimeParseException ignorada) {
            // próximo formato
        }
        try {
            return LocalDate.parse(texto).atStartOfDay();
        } catch (DateTimeParseException ignorada) {
            // próximo formato
        }
        try {
            return LocalDateTime.parse(texto, BR_DATA_HORA);
        } catch (DateTimeParseException ignorada) {
            // próximo formato
        }
        try {
            return LocalDate.parse(texto, BR_DATA).atStartOfDay();
        } catch (DateTimeParseException ignorada) {
            return null;
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

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}
