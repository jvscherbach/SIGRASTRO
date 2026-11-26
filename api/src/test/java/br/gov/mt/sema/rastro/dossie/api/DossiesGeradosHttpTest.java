package br.gov.mt.sema.rastro.dossie.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.dossie.application.port.RepositorioDePedidos;
import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/** Contrato de GET /api/dossies, "Dossiês gerados" (S4, bloco 7). Geração síncrona, arquivos falsos, dados fictícios. */
@org.springframework.test.context.TestPropertySource(properties = "rastro.conversao.habilitada=false")
class DossiesGeradosHttpTest extends BaseHttpDeDocumentos {

    @Autowired
    RepositorioDePedidos repositorio;

    private final List<MockHttpServletResponse> respostas = new ArrayList<>();

    @BeforeEach
    void limparHistorico() {
        repositorio.todos().forEach(p -> repositorio.remover(p.id()));
        respostas.clear();
    }

    private String criar(String usuario, String... referencias) throws Exception {
        List<Map<String, String>> docs = new ArrayList<>();
        for (int i = 0; i < referencias.length; i += 2) {
            docs.add(Map.of("origem", referencias[i], "id", referencias[i + 1]));
        }
        ResultActions criacao = guardar(mvc.perform(post("/api/dossies").header("Authorization", bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idRequerimento", 101, "documentos", docs)))))
                .andExpect(status().isAccepted());
        return json.readTree(criacao.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions listar(String usuario, String consulta) throws Exception {
        return guardar(mvc.perform(get("/api/dossies" + consulta).header("Authorization", bearer(usuario))));
    }

    private JsonNode lerJson(ResultActions acoes) throws Exception {
        return json.readTree(acoes.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions guardar(ResultActions acoes) {
        respostas.add(acoes.andReturn().getResponse());
        return acoes;
    }

    private static List<String> ids(JsonNode pagina) {
        List<String> ids = new ArrayList<>();
        pagina.get("conteudo").forEach(i -> ids.add(i.get("id").asText()));
        return ids;
    }

    @Test
    void operadorVeSoOsSeusSemQuemGerou() throws Exception {
        String doOperador = criar("operador", "DOCUMENTO_PROCESSO", "804328");
        criar("gestor", "DOCUMENTO_PROCESSO", "804328", "COMPLEMENTACAO", "900118");

        JsonNode pagina = lerJson(listar("operador", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].geradoPor").doesNotExist()));

        assertThat(ids(pagina)).containsExactly(doOperador);
        assertThat(pagina.get("totalElementos").asInt()).isEqualTo(1);
        // somenteMeus não muda nada para o Operador
        assertThat(ids(lerJson(listar("operador", "?somenteMeus=false")))).containsExactly(doOperador);
    }

    @Test
    void gestorEAdministradorVeemTodosComQuemGerouEFiltramOsSeus() throws Exception {
        String doOperador = criar("operador", "DOCUMENTO_PROCESSO", "804328");
        String doGestor = criar("gestor", "DOCUMENTO_PROCESSO", "804328");

        for (String quem : new String[] {"gestor", "admin"}) {
            JsonNode todos = lerJson(listar(quem, "").andExpect(status().isOk()));
            assertThat(ids(todos)).containsExactlyInAnyOrder(doOperador, doGestor);
            todos.get("conteudo").forEach(i -> assertThat(i.get("geradoPor").asText()).isNotBlank());
        }
        assertThat(ids(lerJson(listar("gestor", "?somenteMeus=true")))).containsExactly(doGestor);
        assertThat(ids(lerJson(listar("admin", "?somenteMeus=true")))).isEmpty();
    }

    @Test
    void itemTrazIdentificacaoSituacaoEContagensSemDadoDeDocumento() throws Exception {
        String id = criar("gestor", "DOCUMENTO_PROCESSO", "804328", "COMPLEMENTACAO", "900118");

        JsonNode item = lerJson(listar("gestor", "")).at("/conteudo/0");

        assertThat(item.get("id").asText()).isEqualTo(id);
        assertThat(item.get("idRequerimento").asLong()).isEqualTo(101);
        assertThat(item.at("/identificacao/rotulo").asText()).isEqualTo("Processo");
        assertThat(item.at("/identificacao/numero").asText()).isEqualTo("2825/2025");
        assertThat(item.get("situacao").asText()).isEqualTo("CONCLUIDO");
        assertThat(item.get("documentos").asInt()).isEqualTo(2);
        assertThat(item.get("falhas").asInt()).isZero();
        assertThat(item.get("listados").asInt()).isEqualTo(6);
        assertThat(item.get("temArquivo").asBoolean()).isTrue();
        assertThat(item.get("arquivoGerado").asBoolean()).isTrue();
        assertThat(item.get("tamanhoBytes").asLong()).isPositive();
        assertThat(item.get("entregaPorTicket").asBoolean()).isFalse();
        assertThat(item.has("criadoEm")).isTrue();
        assertThat(item.has("expiraEm")).isTrue();
        assertThat(item.has("itens")).isFalse();
    }

    @Test
    void baixadoAparecaComoBaixadoSemArquivo() throws Exception {
        String id = criar("gestor", "DOCUMENTO_PROCESSO", "804328");
        guardar(mvc.perform(get("/api/dossies/" + id + "/arquivo?disposicao=attachment")
                .header("Authorization", bearer("gestor")))).andExpect(status().isOk());

        listar("gestor", "").andExpect(jsonPath("$.conteudo[0].situacao").value("BAIXADO"))
                .andExpect(jsonPath("$.conteudo[0].temArquivo").value(false))
                .andExpect(jsonPath("$.conteudo[0].tamanhoBytes").isEmpty());
    }

    @Test
    void pedidoAlheioPorIdContinua404ParaOOperador() throws Exception {
        String doGestor = criar("gestor", "DOCUMENTO_PROCESSO", "804328");

        guardar(mvc.perform(get("/api/dossies/" + doGestor).header("Authorization", bearer("operador"))))
                .andExpect(status().isNotFound());
        assertThat(ids(lerJson(listar("operador", "")))).isEmpty();
    }

    @Test
    void paginacaoEParametrosInvalidos() throws Exception {
        for (int i = 0; i < 3; i++) {
            criar("gestor", "DOCUMENTO_PROCESSO", "804328");
        }

        listar("gestor", "?pagina=1&tamanho=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(1))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.tamanho").value(2));
        listar("gestor", "?tamanho=51").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        listar("gestor", "?pagina=-1").andExpect(status().isBadRequest());
        guardar(mvc.perform(get("/api/dossies"))).andExpect(status().isUnauthorized());
    }

    @Test
    void nenhumaRespostaTrazNomeDeArquivoTituloChaveOuToken() throws Exception {
        criar("operador", "DOCUMENTO_PROCESSO", "804328");
        criar("gestor", "DOCUMENTO_PROCESSO", "804328", "DOCUMENTO_PROCESSO", "804329");
        listar("operador", "");
        listar("gestor", "");
        listar("admin", "?somenteMeus=true");

        String[] proibidos = {"requerimento.pdf", "Requerimento assinado", "laudo-reservado", "Laudo reservado",
                "oficio.pdf", CHAVE_1, CHAVE_SIGILOSA, TOKEN_DE_ARQUIVOS, "CHAVE-INTERNA"};
        int listagens = 0;
        for (MockHttpServletResponse resposta : respostas) {
            if (!resposta.getContentAsString().contains("\"conteudo\"")) {
                continue;
            }
            listagens++;
            StringBuilder tudo = new StringBuilder(resposta.getContentAsString(StandardCharsets.UTF_8));
            resposta.getHeaderNames().forEach(n -> tudo.append('\n').append(n).append(": ")
                    .append(String.join(",", resposta.getHeaders(n))));
            for (String proibido : proibidos) {
                assertThat(tudo.toString()).doesNotContain(proibido);
            }
        }
        assertThat(listagens).isEqualTo(3);
    }
}
