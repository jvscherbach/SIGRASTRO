package br.gov.mt.sema.vistas.documento.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import br.gov.mt.sema.vistas.documento.application.port.PessoasDoRequerimento;
import br.gov.mt.sema.vistas.documento.application.port.PessoasDoRequerimento.Pessoa;
import br.gov.mt.sema.vistas.documento.domain.PapelDaPessoa;
import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.testsupport.BaseHttpDeDocumentos;
import br.gov.mt.sema.vistas.testsupport.ServidorHttpDeTeste;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Documentos pessoais do SCORP (S4, bloco 5) pelo contrato HTTP: o SCORP é um servidor HTTP local de teste (cliente
 * real), o SIGA e a API de arquivos são falsos. Sem SCORP_API_TOKEN_FILE o cliente usa o token da API de arquivos.
 */
class ScorpHttpTest extends BaseHttpDeDocumentos {

    private static final String PESSOA_INTERESSADO = "CORP-INT-0001";
    private static final String PESSOA_TECNICO = "CORP-RT-0002";
    private static final String CHAVE_RG = "CHAVE-INTERNA-SCORP-RG";
    private static final String CHAVE_ART = "CHAVE-INTERNA-SCORP-ART";
    private static final ServidorHttpDeTeste SCORP;

    static {
        try {
            SCORP = ServidorHttpDeTeste.iniciar();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void scorp(DynamicPropertyRegistry registro) {
        registro.add("vistas.scorp.url", SCORP::url);
    }

    @AfterAll
    static void encerrar() {
        SCORP.close();
    }

    @MockitoBean
    PessoasDoRequerimento pessoas;

    private final Map<String, Integer> statusPorPessoa = new ConcurrentHashMap<>();
    private final List<String> chamadas = new CopyOnWriteArrayList<>();
    private final List<String> autorizacoes = new CopyOnWriteArrayList<>();
    private final List<MockHttpServletResponse> respostas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        statusPorPessoa.clear();
        chamadas.clear();
        autorizacoes.clear();
        respostas.clear();
        when(pessoas.listarPessoas(anyLong())).thenReturn(List.of(
                new Pessoa(PESSOA_INTERESSADO, PapelDaPessoa.INTERESSADO),
                new Pessoa(PESSOA_TECNICO, PapelDaPessoa.RESPONSAVEL_TECNICO)));
        Map<String, String> corpos = Map.of(
                PESSOA_INTERESSADO, "[{\"chave\":\"" + CHAVE_RG + "\",\"nomeArquivo\":\"rg.pdf\",\"descricao\":\"RG do "
                        + "interessado\",\"dataInicio\":\"2025-03-01T08:00:00\",\"dataFim\":null}]",
                PESSOA_TECNICO, "[{\"chave\":\"" + CHAVE_ART + "\",\"nomeArquivo\":\"art.pdf\",\"descricao\":\"ART do "
                        + "responsável\",\"dataInicio\":\"2025-03-02T08:00:00\",\"dataFim\":null}]");
        SCORP.responderCom(troca -> {
            String caminho = troca.caminhoBruto();
            String pessoa = URLDecoder.decode(caminho.substring(caminho.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
            chamadas.add(pessoa);
            autorizacoes.add(String.valueOf(troca.cabecalho("Authorization")));
            int status = statusPorPessoa.getOrDefault(pessoa, 200);
            troca.responder(status, status == 200 ? corpos.getOrDefault(pessoa, "[]").getBytes(StandardCharsets.UTF_8)
                    : new byte[0]);
        });
    }

    @Test
    void listaTrazOrigemPessoalComOPapelSemAvisos() throws Exception {
        JsonNode corpo = lerJson(listar("gestor.demo").andExpect(status().isOk()));

        List<JsonNode> pessoais = new ArrayList<>();
        corpo.get("itens").forEach(i -> {
            if (i.get("origem").asText().equals("SCORP")) {
                pessoais.add(i);
            }
        });
        assertThat(pessoais).hasSize(2);
        assertThat(pessoais.get(0).get("origemDescricao").asText()).isEqualTo("Pessoal · Interessado");
        assertThat(pessoais.get(0).get("titulo").asText()).isEqualTo("RG do interessado");
        assertThat(pessoais.get(0).get("id").asText()).matches("[0-9a-f]{16}");
        assertThat(pessoais.get(0).get("selecionavelPorPadrao").asBoolean()).isTrue();
        assertThat(pessoais.get(1).get("origemDescricao").asText()).isEqualTo("Pessoal · Responsável técnico");
        assertThat(corpo.get("avisos")).isEmpty();
        assertThat(corpo.at("/totais/total").asInt()).isEqualTo(8);
        assertThat(chamadas).containsExactlyInAnyOrder(PESSOA_INTERESSADO, PESSOA_TECNICO);
        assertThat(autorizacoes).allMatch(a -> a.equals("Bearer " + TOKEN_DE_ARQUIVOS));
    }

    @Test
    void scorpForaDoArNaoImpedeAListaEAvisaUmaVez() throws Exception {
        statusPorPessoa.put(PESSOA_INTERESSADO, 404);
        statusPorPessoa.put(PESSOA_TECNICO, 401);

        JsonNode corpo = lerJson(listar("gestor.demo").andExpect(status().isOk()));

        assertThat(corpo.get("avisos")).hasSize(1);
        assertThat(corpo.at("/avisos/0/codigo").asText()).isEqualTo("SCORP_INDISPONIVEL");
        assertThat(corpo.at("/avisos/0/mensagem").asText()).contains("Os demais documentos foram listados")
                .doesNotContain("401", "404", "HTTP", "CORP-");
        assertThat(corpo.get("itens")).hasSize(6);
        assertThat(corpo.toString()).doesNotContain("\"SCORP\"");
    }

    @Test
    void falhaParcialAvisaComCodigoProprio() throws Exception {
        statusPorPessoa.put(PESSOA_TECNICO, 404);

        listar("gestor.demo").andExpect(status().isOk())
                .andExpect(jsonPath("$.avisos.length()").value(1))
                .andExpect(jsonPath("$.avisos[0].codigo").value("SCORP_PARCIAL"))
                .andExpect(jsonPath("$.itens.length()").value(7));
    }

    @Test
    void operadorDeOutroInteressadoNaoChegaAoScorp() throws Exception {
        guardar(mvc.perform(get("/api/requerimentos/202/documentos").header("Authorization", bearer("operador.demo"))))
                .andExpect(status().isForbidden());
        verify(pessoas, never()).listarPessoas(anyLong());
        assertThat(chamadas).isEmpty();
    }

    @Test
    void documentoPessoalAbreNoModal() throws Exception {
        String id = idPessoal("RG do interessado");

        guardar(mvc.perform(get("/api/documentos/SCORP/" + id + "/conteudo?requerimento=101")
                .header("Authorization", bearer("operador.demo"))))
                .andExpect(status().isOk());
        verify(arquivos).baixar(eq(CHAVE_RG), any(Path.class));
    }

    @Test
    void dossieComDocumentoPessoalSaiComOrigemPessoalNoSumario() throws Exception {
        String id = idPessoal("RG do interessado");

        String pedido = criar("gestor.demo", "SCORP", id, "DOCUMENTO_PROCESSO", "804328");
        guardar(mvc.perform(get("/api/dossies/" + pedido).header("Authorization", bearer("gestor.demo"))))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.itens[0].origem").value("SCORP"));
        byte[] pdf = guardar(mvc.perform(get("/api/dossies/" + pedido + "/arquivo?disposicao=inline")
                .header("Authorization", bearer("gestor.demo")))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument documento = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String texto = new org.apache.pdfbox.text.PDFTextStripper().getText(documento);
            assertThat(texto).contains("Pessoal", "RG do interessado", "2 documentos incluídos de 8 listados");
            assertThat(texto).doesNotContain(CHAVE_RG, PESSOA_INTERESSADO);
        }
    }

    @Test
    void documentoPessoalQueFalhaDeixaOPedidoAguardandoDecisao() throws Exception {
        doThrow(new FalhaNoDocumentoException("arquivo não encontrado no repositório"))
                .when(arquivos).baixar(eq(CHAVE_ART), any(Path.class));
        String id = idPessoal("ART do responsável");

        String pedido = criar("gestor.demo", "SCORP", id);
        guardar(mvc.perform(get("/api/dossies/" + pedido).header("Authorization", bearer("gestor.demo"))))
                .andExpect(jsonPath("$.status").value("PENDENTE_DECISAO"))
                .andExpect(jsonPath("$.falhas[0].origem").value("SCORP"))
                .andExpect(jsonPath("$.falhas[0].motivo").value("arquivo não encontrado no repositório"));
    }

    @Test
    void comOScorpForaDoArADecisaoNaoChegaAoDossieComMotivoClaro() throws Exception {
        String id = idPessoal("RG do interessado");
        statusPorPessoa.put(PESSOA_INTERESSADO, 404);

        guardar(mvc.perform(post("/api/dossies").header("Authorization", bearer("gestor.demo"))
                .contentType(MediaType.APPLICATION_JSON).content(corpo("SCORP", id))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("o SCORP não respondeu agora")));
        verify(fila, never()).submeter(any());
    }

    @Test
    void nenhumaRespostaTrazTokenChaveUrlOuPessoaDoScorp() throws Exception {
        listar("gestor.demo");
        listar("operador.demo");
        statusPorPessoa.put(PESSOA_TECNICO, 401);
        listar("gestor.demo");
        String id = idPessoal("RG do interessado");
        String pedido = criar("gestor.demo", "SCORP", id);
        guardar(mvc.perform(get("/api/dossies/" + pedido).header("Authorization", bearer("gestor.demo"))));

        List<String> proibidos = new ArrayList<>(List.of(SEGREDOS));
        proibidos.addAll(Set.of(CHAVE_RG, CHAVE_ART, PESSOA_INTERESSADO, PESSOA_TECNICO, SCORP.url(), "127.0.0.1",
                "pessoa-licenciamento"));
        assertThat(respostas).hasSizeGreaterThanOrEqualTo(5);
        for (MockHttpServletResponse resposta : respostas) {
            StringBuilder tudo = new StringBuilder(resposta.getContentAsString(StandardCharsets.UTF_8));
            for (String nome : resposta.getHeaderNames()) {
                tudo.append('\n').append(nome).append(": ").append(String.join(",", resposta.getHeaders(nome)));
            }
            for (String proibido : proibidos) {
                assertThat(tudo.toString()).doesNotContain(proibido);
            }
        }
    }

    // --- apoio

    private ResultActions listar(String usuario) throws Exception {
        return guardar(mvc.perform(get("/api/requerimentos/101/documentos").header("Authorization", bearer(usuario))));
    }

    private String idPessoal(String titulo) throws Exception {
        JsonNode corpo = json.readTree(mvc.perform(get("/api/requerimentos/101/documentos")
                .header("Authorization", bearer("gestor.demo"))).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode item : corpo.get("itens")) {
            if (item.get("origem").asText().equals("SCORP") && item.get("titulo").asText().equals(titulo)) {
                return item.get("id").asText();
            }
        }
        throw new AssertionError("documento pessoal não listado: " + titulo);
    }

    private String corpo(String... referencias) throws Exception {
        List<Map<String, String>> docs = new ArrayList<>();
        for (int i = 0; i < referencias.length; i += 2) {
            docs.add(Map.of("origem", referencias[i], "id", referencias[i + 1]));
        }
        return json.writeValueAsString(Map.of("idRequerimento", 101, "documentos", docs));
    }

    private String criar(String usuario, String... referencias) throws Exception {
        ResultActions criacao = guardar(mvc.perform(post("/api/dossies").header("Authorization", bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(corpo(referencias))))
                .andExpect(status().isAccepted());
        return json.readTree(criacao.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions guardar(ResultActions acoes) {
        respostas.add(acoes.andReturn().getResponse());
        return acoes;
    }

    private JsonNode lerJson(ResultActions acoes) throws Exception {
        return json.readTree(acoes.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
