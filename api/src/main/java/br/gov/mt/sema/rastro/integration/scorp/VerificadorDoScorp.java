package br.gov.mt.sema.rastro.integration.scorp;

import java.time.Duration;

import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.rastro.documento.application.port.OrigensLigadas;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * Teste do SCORP (S6, bloco 4): um {@code GET} dos documentos de uma pessoa fictícia, sem ler corpo, com a <b>mesma</b>
 * base de URL ({@code SCORP_API_URL}, terminando em {@code /api}), o mesmo caminho, a mesma credencial e o mesmo tipo de
 * cliente da consulta real ({@link AcessoAoScorp}), mudando só o identificador. Não há rota de saúde conhecida (R55).
 *
 * <p>O identificador fictício não é um id corporativo válido: o SCORP responde 400 (ou 404), o que prova que está no ar
 * e aceitou a credencial; antes isso aparecia como falha (falso alarme). 2xx é OK, 4xx (menos 401/403) alcançável com
 * ressalva, 401/403 falha de autenticação, 503 isolado ganha uma nova tentativa, e DNS, recusa, TLS e tempo esgotado são
 * falha. Tempo limite do teste em {@code CONEXAO_TESTE_TIMEOUT_SEGUNDOS} (padrão 15; antes 5 s fixos, menos que os 15 s
 * da consulta real).
 */
@Component
@Order(3)
class VerificadorDoScorp implements VerificadorDeConexao {

    private static final Logger log = LoggerFactory.getLogger(VerificadorDoScorp.class);

    static final String IDENTIFICADOR = "verificacao-de-conexao";
    /** Mesma espera da consulta real antes da nova tentativa. */
    static final Duration ESPERA_DA_NOVA_TENTATIVA = DocumentosDoScorpHttp.ESPERA_DA_NOVA_TENTATIVA;

    /** Pausa antes da nova tentativa; nos testes, não espera de verdade. */
    interface Espera {
        void aguardar(Duration duracao) throws InterruptedException;
    }

    private final AcessoAoScorp acesso;
    private final OrigensLigadas origens;
    private final CredencialDoScorp credencial;
    private final Duration tempoLimite;
    private final Espera espera;

    @Autowired
    VerificadorDoScorp(@Value("${rastro.scorp.url:}") String url, OrigensLigadas origens,
            @Value("${rastro.scorp.token-file:}") String arquivoDoToken,
            @Value("${rastro.arquivos.token-file:}") String arquivoDoTokenDoLicenciamento,
            @Value("${rastro.arquivos.token:}") String token,
            @Value("${rastro.scorp.auth-mode:}") String modo,
            @Value("${rastro.arquivos.auth-mode:bearer}") String modoDoLicenciamento,
            @Value("${rastro.scorp.cookie-name:}") String nomeDoCookie,
            @Value("${rastro.arquivos.cookie-name:}") String nomeDoCookieDoLicenciamento,
            @Value("${rastro.conexoes.teste-timeout-segundos:15}") int tempoLimiteSegundos) {
        // Mesma escolha de modo e de cookie da consulta real (DocumentosDoScorpHttp).
        this(url, origens, CredencialDoScorp.de(token, arquivoDoToken, arquivoDoTokenDoLicenciamento),
                modo == null || modo.isBlank() ? modoDoLicenciamento : modo,
                nomeDoCookie == null || nomeDoCookie.isBlank() ? nomeDoCookieDoLicenciamento : nomeDoCookie,
                Duration.ofSeconds(Math.max(1, tempoLimiteSegundos)), duracao -> Thread.sleep(duracao.toMillis()));
    }

    VerificadorDoScorp(String url, OrigensLigadas origens, CredencialDoScorp credencial, String modo, String nomeDoCookie,
            Duration tempoLimite, Espera espera) {
        this.acesso = new AcessoAoScorp(url, modo, nomeDoCookie);
        this.origens = origens;
        this.credencial = credencial;
        this.tempoLimite = tempoLimite;
        this.espera = espera;
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
        if (!acesso.configurado()) {
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
            return Resultado.falha("Credencial não configurada.");
        }
        try {
            int status = chamar(valor);
            if (status == 503) {
                log.info("Teste do SCORP: HTTP 503; nova tentativa");
                espera.aguardar(ESPERA_DA_NOVA_TENTATIVA);
                status = chamar(valor);
                if (status == 503) {
                    return Resultado.indisponivelPersistente(status);
                }
            }
            return Resultado.deHttp(status, "um identificador de pessoa fictício", "token");
        } catch (ResourceAccessException e) {
            return Resultado.deRede(e, (int) tempoLimite.toSeconds());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultado.falha("Teste interrompido.");
        } catch (RuntimeException e) {
            log.warn("Teste do SCORP falhou: {}", e.getClass().getSimpleName());
            return Resultado.falha("Não foi possível testar a conexão.");
        }
    }

    private int chamar(String valor) {
        return AcessoAoScorp.cliente(tempoLimite).get().uri(acesso.documentosDaPessoa(IDENTIFICADOR))
                .headers(h -> acesso.autenticar(h, valor))
                .exchange((requisicao, resposta) -> resposta.getStatusCode().value(), true);
    }
}
