package br.gov.mt.sema.rastro.dossie.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.dossie.application.port.EspacoEmDisco;
import br.gov.mt.sema.rastro.shared.application.exception.ArmazenamentoInsuficienteException;
import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Disco cheio e arquivo final perdido pela API (S6, bloco 3): 507 no POST sem criar pedido, FALHA com o código no
 * andamento e na lista, e erro real (nunca 200) no download. O espaço livre é simulado pela porta: nenhum disco é
 * enchido. Nenhuma resposta traz o caminho do diretório temporário.
 */
class ArmazenamentoHttpTest extends BaseHttpDeDocumentos {

    private static final long MB = 1024L * 1024;

    @MockitoBean
    EspacoEmDisco espaco;

    @DynamicPropertySource
    static void espacoMinimo(DynamicPropertyRegistry registro) {
        registro.add("rastro.dossie.espaco-minimo-mb", () -> "500");
    }

    @BeforeEach
    void muitoEspaco() {
        reset(espaco);
        when(espaco.livreEmBytes(any(Path.class))).thenReturn(100_000 * MB);
    }

    private ResultActions criar(String usuario) throws Exception {
        return mvc.perform(post("/api/dossies").header("Authorization", bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idRequerimento", 101, "documentos",
                        List.of(Map.of("origem", "DOCUMENTO_PROCESSO", "id", "804328"))))));
    }

    private String criarComSucesso() throws Exception {
        String corpo = criar("gestor.demo").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asText();
    }

    private void semCaminho(ResultActions resposta) throws Exception {
        String corpo = resposta.andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain(diretorioTemporario().toString()).doesNotContain("dossie.pdf")
                .doesNotContain(".original");
        for (String segredo : SEGREDOS) {
            assertThat(corpo).doesNotContain(segredo);
        }
    }

    @Test
    void postSemEspacoMinimoEh507ProblemDetailSemCriarPedido() throws Exception {
        long antes = totalDePedidos();
        when(espaco.livreEmBytes(any(Path.class))).thenReturn(500 * MB - 1);

        ResultActions resposta = criar("gestor.demo")
                .andExpect(status().isInsufficientStorage())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("ARMAZENAMENTO_INSUFICIENTE"))
                .andExpect(jsonPath("$.detail").value(ArmazenamentoInsuficienteException.MENSAGEM_DO_DOSSIE))
                .andExpect(jsonPath("$.correlationId").exists());
        semCaminho(resposta);

        assertThat(totalDePedidos()).as("nenhum pedido criado").isEqualTo(antes);
    }

    private long totalDePedidos() throws Exception {
        String corpo = mvc.perform(get("/api/dossies").header("Authorization", bearer("gestor.demo"))
                .param("somenteMeus", "true")).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("totalElementos").asLong();
    }

    @Test
    void semEspacoNaObtencaoOAndamentoEAListaMostramFalhaComCodigoEODownloadNaoEh200() throws Exception {
        doThrow(new ArmazenamentoInsuficienteException()).when(arquivos).baixar(anyString(), any(Path.class));

        String id = criarComSucesso();

        semCaminho(mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("FALHA"))
                .andExpect(jsonPath("$.erro.codigo").value("ARMAZENAMENTO_INSUFICIENTE"))
                .andExpect(jsonPath("$.erro.motivo").value(ArmazenamentoInsuficienteException.MENSAGEM_DO_DOSSIE))
                .andExpect(jsonPath("$.tamanhoBytes").doesNotExist()));
        mvc.perform(get("/api/dossies/" + id + "/arquivo").param("disposicao", "attachment")
                        .header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DOSSIE_SEM_ARQUIVO"));
        mvc.perform(get("/api/dossies").header("Authorization", bearer("gestor.demo")).param("somenteMeus", "true"))
                .andExpect(jsonPath("$.conteudo[0].situacao").value("FALHA"))
                .andExpect(jsonPath("$.conteudo[0].falha.codigo").value("ARMAZENAMENTO_INSUFICIENTE"))
                .andExpect(jsonPath("$.conteudo[0].temArquivo").value(false));
        assertThat(diretorioTemporario().resolve(id)).doesNotExist();
    }

    @Test
    void arquivoQueSumiuDoDiscoEh410ComCodigoEOPedidoViraFalha() throws Exception {
        String id = criarComSucesso();
        Files.delete(diretorioTemporario().resolve(id).resolve("dossie.pdf"));

        semCaminho(mvc.perform(get("/api/dossies/" + id + "/arquivo").param("disposicao", "attachment")
                        .header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("DOSSIE_ARQUIVO_AUSENTE")));
        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("FALHA"))
                .andExpect(jsonPath("$.erro.codigo").value("DOSSIE_ARQUIVO_AUSENTE"));
    }

    @Test
    void arquivoTruncadoEh500ComCodigoENuncaUmPdfIncompleto() throws Exception {
        String id = criarComSucesso();
        Files.write(diretorioTemporario().resolve(id).resolve("dossie.pdf"), "%PDF-1.7 cortado".getBytes());

        semCaminho(mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("DOSSIE_ARQUIVO_INVALIDO")));
        assertThat(diretorioTemporario().resolve(id)).doesNotExist();
    }

    @Test
    void ticketDeArquivoQueSumiuTambemEhErroReal() throws Exception {
        String id = criarComSucesso();
        Files.delete(diretorioTemporario().resolve(id).resolve("dossie.pdf"));

        mvc.perform(post("/api/dossies/" + id + "/arquivo/ticket").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.errorCode").value("DOSSIE_ARQUIVO_AUSENTE"));
    }
}
