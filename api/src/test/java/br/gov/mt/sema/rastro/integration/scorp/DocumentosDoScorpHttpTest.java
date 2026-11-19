package br.gov.mt.sema.rastro.integration.scorp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.documento.application.port.DocumentosPessoais.Consulta;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosPessoais.DocumentoPessoal;
import br.gov.mt.sema.rastro.testsupport.ServidorHttpDeTeste;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * Cliente do SCORP (S4) contra um servidor HTTP local de teste: nenhum SCORP real. Identificadores, chaves e tokens são
 * marcadores fictícios que nunca podem aparecer em log, erro ou resultado (exceto a chave, no próprio item).
 */
class DocumentosDoScorpHttpTest {

    private static final String TOKEN = "TOKEN-SCORP-DE-TESTE";
    private static final String TOKEN_DO_ARQUIVO = "TOKEN-SCORP-ARQUIVO-DE-TESTE";
    private static final String PESSOA_1 = "ID-CORP-0001";
    private static final String PESSOA_2 = "ID-CORP-0002";
    private static final String JSON_DE_DUAS = "[{\"chave\":\"CHAVE-SCORP-1\",\"nomeArquivo\":\"rg.pdf\",\"descricao\":\"RG\","
            + "\"dataInicio\":\"2024-05-10T09:30:00\",\"dataFim\":null},"
            + "{\"chave\":\"CHAVE-SCORP-2\",\"nomeArquivo\":\"comprovante.png\",\"descricao\":null,"
            + "\"dataInicio\":\"2024-05-11\",\"dataFim\":\"2024-06-01T00:00:00\",\"campoNovo\":1}]";

    @TempDir
    Path pasta;

    private ServidorHttpDeTeste servidor;
    private final ObjectMapper json = JsonMapper.builder().build();
    private final List<String> pedidos = new CopyOnWriteArrayList<>();
    private final List<String> autorizacoes = new CopyOnWriteArrayList<>();
    private final List<String> cookies = new CopyOnWriteArrayList<>();
    private final List<Duration> esperas = new CopyOnWriteArrayList<>();
    private ListAppender<ILoggingEvent> log;

    @BeforeEach
    void subir() throws IOException {
        servidor = ServidorHttpDeTeste.iniciar();
        log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger("br.gov.mt.sema.rastro")).addAppender(log);
    }

    @AfterEach
    void derrubar() {
        servidor.close();
        ((Logger) LoggerFactory.getLogger("br.gov.mt.sema.rastro")).detachAppender(log);
    }

    private void rota(Map<String, Integer> statusPorPessoa, Map<String, String> corpoPorPessoa) {
        servidor.responderCom(troca -> {
            String caminho = troca.caminhoBruto();
            pedidos.add(caminho);
            autorizacoes.add(String.valueOf(troca.cabecalho("Authorization")));
            cookies.add(String.valueOf(troca.cabecalho("Cookie")));
            String pessoa = URLDecoder.decode(caminho.substring(caminho.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
            int status = statusPorPessoa.getOrDefault(pessoa, 200);
            troca.responder(status, (status == 200 ? corpoPorPessoa.getOrDefault(pessoa, "[]") : "")
                    .getBytes(StandardCharsets.UTF_8));
        });
    }

    private DocumentosDoScorpHttp adapter(CredencialDoScorp credencial, String modo, int simultaneas) {
        return new DocumentosDoScorpHttp(servidor.url() + "/", true, credencial, modo, "SESSAO",
                Duration.ofSeconds(1), simultaneas, json, esperas::add);
    }

    private DocumentosDoScorpHttp adapter() {
        return adapter(CredencialDoScorp.de(TOKEN, null, null), "bearer", 4);
    }

    private static Set<String> pessoas(String... ids) {
        return new LinkedHashSet<>(List.of(ids));
    }

    // --- sucesso

    @Test
    void consultaPorPessoaNoCaminhoDocumentadoEInterpretaOsCampos() {
        rota(Map.of(), Map.of(PESSOA_1, JSON_DE_DUAS));

        Consulta consulta = adapter().consultar(pessoas(PESSOA_1));

        assertThat(pedidos).containsExactly("/documentos/pessoa-licenciamento/" + PESSOA_1);
        assertThat(autorizacoes).containsExactly("Bearer " + TOKEN);
        assertThat(consulta.falharam()).isEmpty();
        List<DocumentoPessoal> itens = consulta.documentos().get(PESSOA_1);
        assertThat(itens).hasSize(2);
        assertThat(itens.get(0).chave()).isEqualTo("CHAVE-SCORP-1");
        assertThat(itens.get(0).nomeArquivo()).isEqualTo("rg.pdf");
        assertThat(itens.get(0).descricao()).isEqualTo("RG");
        assertThat(itens.get(0).dataInicio()).isEqualTo(LocalDateTime.of(2024, 5, 10, 9, 30));
        assertThat(itens.get(0).dataFim()).isNull();
        assertThat(itens.get(1).descricao()).isNull();
        assertThat(itens.get(1).dataInicio()).isEqualTo(LocalDateTime.of(2024, 5, 11, 0, 0));
        assertThat(itens.get(1).dataFim()).isEqualTo(LocalDateTime.of(2024, 6, 1, 0, 0));
        assertThat(itens.get(0).toString()).doesNotContain("CHAVE-SCORP");
    }

    @Test
    void identificadorDaPessoaVaiCodificadoNumSoSegmento() {
        rota(Map.of(), Map.of());

        adapter().consultar(pessoas("a/../b c"));

        assertThat(pedidos).containsExactly("/documentos/pessoa-licenciamento/a%2F..%2Fb%20c");
    }

    @Test
    void modoCookie() {
        rota(Map.of(), Map.of());

        adapter(CredencialDoScorp.de(TOKEN, null, null), "cookie", 4).consultar(pessoas(PESSOA_1));

        assertThat(cookies).containsExactly("SESSAO=" + TOKEN);
        assertThat(autorizacoes).containsExactly("null");
    }

    @Test
    void formatosDeDataAceitosEDesconhecidoViraNulo() throws IOException {
        assertThat(DocumentosDoScorpHttp.data(json.readTree("\"2024-05-10T12:00:00Z\"")))
                .isEqualTo(LocalDateTime.of(2024, 5, 10, 8, 0));
        assertThat(DocumentosDoScorpHttp.data(json.readTree("\"10/05/2024\""))).isEqualTo(LocalDateTime.of(2024, 5, 10, 0, 0));
        assertThat(DocumentosDoScorpHttp.data(json.readTree("\"10/05/2024 14:20\"")))
                .isEqualTo(LocalDateTime.of(2024, 5, 10, 14, 20));
        assertThat(DocumentosDoScorpHttp.data(json.readTree("1715342400000"))).isEqualTo(LocalDateTime.of(2024, 5, 10, 8, 0));
        assertThat(DocumentosDoScorpHttp.data(json.readTree("\"ontem\""))).isNull();
        assertThat(DocumentosDoScorpHttp.data(json.readTree("\"\""))).isNull();
    }

    // --- falhas: nunca lança, a pessoa vai para "falharam"

    @Test
    void credencialRecusadaNaoEncontradoERespostaInvalidaFalhamSemNovaTentativa() {
        Map<String, Integer> status = Map.of("P401", 401, "P403", 403, "P404", 404, "P418", 418);
        Map<String, String> corpos = Map.of("OBJ", "{\"chave\":\"x\"}", "LIXO", "<html>", "MISTO", "[1, 2]");
        rota(status, corpos);

        Consulta consulta = adapter().consultar(pessoas("P401", "P403", "P404", "P418", "OBJ", "LIXO", "MISTO", PESSOA_1));

        assertThat(consulta.falharam()).containsExactlyInAnyOrder("P401", "P403", "P404", "P418", "OBJ", "LIXO", "MISTO");
        assertThat(consulta.documentos()).containsOnlyKeys(PESSOA_1);
        assertThat(pedidos).hasSize(8);
        assertThat(esperas).isEmpty();
    }

    @Test
    void erroDoServidorTemUmaNovaTentativaSo() {
        rota(Map.of(PESSOA_1, 500, PESSOA_2, 503), Map.of());

        Consulta consulta = adapter().consultar(pessoas(PESSOA_1, PESSOA_2));

        assertThat(consulta.falharam()).containsExactlyInAnyOrder(PESSOA_1, PESSOA_2);
        assertThat(pedidos).hasSize(4);
        assertThat(esperas).hasSize(2).allMatch(DocumentosDoScorpHttp.ESPERA_DA_NOVA_TENTATIVA::equals);
    }

    @Test
    void segundaTentativaQueDaCertoRecuperaAPessoa() {
        AtomicInteger chamadas = new AtomicInteger();
        servidor.responderCom(troca -> {
            pedidos.add(troca.caminhoBruto());
            troca.responder(chamadas.incrementAndGet() == 1 ? 502 : 200, JSON_DE_DUAS.getBytes(StandardCharsets.UTF_8));
        });

        Consulta consulta = adapter().consultar(pessoas(PESSOA_1));

        assertThat(consulta.falharam()).isEmpty();
        assertThat(consulta.documentos().get(PESSOA_1)).hasSize(2);
        assertThat(pedidos).hasSize(2);
    }

    @Test
    void tempoLimiteEhFalhaComUmaNovaTentativa() {
        servidor.responderCom(troca -> {
            pedidos.add(troca.caminhoBruto());
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Consulta consulta = adapter().consultar(pessoas(PESSOA_1));

        assertThat(consulta.falharam()).containsExactly(PESSOA_1);
        assertThat(pedidos).hasSize(2);
        assertThat(log.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("tempo limite"));
    }

    @Test
    void scorpForaDoArFalhaTodasAsPessoas() {
        String url = servidor.url();
        servidor.close();
        DocumentosDoScorpHttp foraDoAr = new DocumentosDoScorpHttp(url, true, CredencialDoScorp.de(TOKEN, null, null),
                "bearer", "", Duration.ofSeconds(1), 2, json, esperas::add);

        Consulta consulta = foraDoAr.consultar(pessoas(PESSOA_1, PESSOA_2));

        assertThat(consulta.falharam()).containsExactlyInAnyOrder(PESSOA_1, PESSOA_2);
        assertThat(consulta.documentos()).isEmpty();
    }

    // --- desligado

    @Test
    void semUrlOuComAOrigemDesligadaNaoChamaNada() {
        rota(Map.of(), Map.of());
        CredencialDoScorp credencial = CredencialDoScorp.de(TOKEN, null, null);

        DocumentosDoScorpHttp semUrl = new DocumentosDoScorpHttp("  ", true, credencial, "bearer", "",
                Duration.ofSeconds(1), 2, json, esperas::add);
        DocumentosDoScorpHttp desligada = new DocumentosDoScorpHttp(servidor.url(), false, credencial, "bearer", "",
                Duration.ofSeconds(1), 2, json, esperas::add);

        assertThat(semUrl.habilitado()).isFalse();
        assertThat(desligada.habilitado()).isFalse();
        assertThat(semUrl.consultar(pessoas(PESSOA_1)).falharam()).isEmpty();
        assertThat(desligada.consultar(pessoas(PESSOA_1)).documentos()).isEmpty();
        assertThat(pedidos).isEmpty();
    }

    // --- paralelismo

    @Test
    void chamadasEmParaleloLimitadas() {
        AtomicInteger emCurso = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        servidor.responderCom(troca -> {
            int agora = emCurso.incrementAndGet();
            maximo.accumulateAndGet(agora, Math::max);
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            emCurso.decrementAndGet();
            troca.responder(200, "[]".getBytes(StandardCharsets.UTF_8));
        });
        Set<String> oito = new LinkedHashSet<>();
        for (int i = 0; i < 8; i++) {
            oito.add("P" + i);
        }

        Consulta consulta = adapter(CredencialDoScorp.de(TOKEN, null, null), "bearer", 2).consultar(oito);

        assertThat(consulta.documentos()).hasSize(8);
        assertThat(maximo.get()).isEqualTo(2);
    }

    // --- credencial

    @Test
    void arquivoDoTokenDoScorpTemPrecedenciaEERelidoACadaChamada() throws IOException {
        rota(Map.of(), Map.of());
        Path doScorp = pasta.resolve("token-scorp");
        Path doLicenciamento = pasta.resolve("token-arquivos");
        Files.writeString(doScorp, TOKEN_DO_ARQUIVO + "\n");
        Files.writeString(doLicenciamento, "TOKEN-LICENCIAMENTO-DE-TESTE");
        DocumentosDoScorpHttp adapter = adapter(CredencialDoScorp.de(TOKEN, doScorp.toString(), doLicenciamento.toString()),
                "bearer", 1);

        adapter.consultar(pessoas(PESSOA_1));
        Files.writeString(doScorp, "TOKEN-SCORP-NOVO-DE-TESTE");
        adapter.consultar(pessoas(PESSOA_1));

        assertThat(autorizacoes).containsExactly("Bearer " + TOKEN_DO_ARQUIVO, "Bearer TOKEN-SCORP-NOVO-DE-TESTE");
    }

    @Test
    void semArquivoProprioUsaOArquivoDoLicenciamento() throws IOException {
        rota(Map.of(), Map.of());
        Path doLicenciamento = pasta.resolve("token-arquivos");
        Files.writeString(doLicenciamento, "TOKEN-LICENCIAMENTO-DE-TESTE");

        adapter(CredencialDoScorp.de(TOKEN, " ", doLicenciamento.toString()), "bearer", 1).consultar(pessoas(PESSOA_1));

        assertThat(autorizacoes).containsExactly("Bearer TOKEN-LICENCIAMENTO-DE-TESTE");
    }

    @Test
    void arquivoDoTokenAusenteOuVazioFalhaSemChamarOScorp() throws IOException {
        rota(Map.of(), Map.of());
        Path vazio = pasta.resolve("vazio");
        Files.writeString(vazio, "  \n");

        Consulta ausente = adapter(CredencialDoScorp.de(TOKEN, pasta.resolve("nao-existe").toString(), null), "bearer", 1)
                .consultar(pessoas(PESSOA_1));
        Consulta semValor = adapter(CredencialDoScorp.de(TOKEN, vazio.toString(), null), "bearer", 1)
                .consultar(pessoas(PESSOA_1));

        assertThat(ausente.falharam()).containsExactly(PESSOA_1);
        assertThat(semValor.falharam()).containsExactly(PESSOA_1);
        assertThat(pedidos).isEmpty();
    }

    // --- varredura das saídas

    @Test
    void nenhumLogResultadoOuCredencialTrazTokenCaminhoUrlOuPessoa() throws IOException {
        Path arquivoDoToken = pasta.resolve("segredo-scorp");
        Files.writeString(arquivoDoToken, TOKEN_DO_ARQUIVO);
        rota(Map.of(PESSOA_2, 401, "P500", 500, "P404", 404), Map.of(PESSOA_1, JSON_DE_DUAS));
        CredencialDoScorp credencial = CredencialDoScorp.de(TOKEN, arquivoDoToken.toString(), null);

        Consulta consulta = adapter(credencial, "cookie", 2).consultar(pessoas(PESSOA_1, PESSOA_2, "P500", "P404"));
        Consulta semArquivo = adapter(CredencialDoScorp.de(TOKEN, pasta.resolve("sumiu").toString(), null), "bearer", 1)
                .consultar(pessoas(PESSOA_1));

        List<String> saidas = new ArrayList<>();
        log.list.forEach(e -> saidas.add(e.getFormattedMessage()));
        saidas.add(credencial.toString());
        saidas.add(consulta.documentos().get(PESSOA_1).toString());
        saidas.add(semArquivo.toString().replace(PESSOA_1, ""));
        assertThat(log.list).isNotEmpty();
        for (String saida : saidas) {
            assertThat(saida).doesNotContain(TOKEN, TOKEN_DO_ARQUIVO, arquivoDoToken.toString(), "segredo-scorp",
                    servidor.url(), "127.0.0.1", "CHAVE-SCORP", "SESSAO=");
        }
        assertThat(log.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain(PESSOA_1, PESSOA_2, "P500", "P404", "pessoa-licenciamento"));
    }
}
