package br.gov.mt.sema.rastro.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

/** Rotas de conexões, sem nenhum sistema externo configurado; os verificadores têm testes próprios contra servidor local. */
class ConexoesHttpTest extends BaseHttpDeDocumentos {

    private static final String CONEXOES = "/api/configuracoes/conexoes";

    private MockHttpServletResponse listar(String usuario) throws Exception {
        return mvc.perform(get(CONEXOES).header(HttpHeaders.AUTHORIZATION, bearer(usuario))).andReturn().getResponse();
    }

    private MockHttpServletResponse testar(String usuario, String nome) throws Exception {
        return mvc.perform(post(CONEXOES + "/" + nome + "/teste").header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
                .andReturn().getResponse();
    }

    @Test
    void lista_na_ordem_da_tela_sem_credencial() throws Exception {
        MockHttpServletResponse resposta = listar("admin");

        JsonNode conexoes = json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8)).get("conexoes");
        List<String> nomes = new ArrayList<>();
        conexoes.forEach(c -> nomes.add(c.get("nome").asText()));
        // S7, bloco 10: o Camunda tem teste próprio, separado do banco do SIGA e da API de arquivos.
        assertThat(nomes).containsExactly("siga", "arquivos", "scorp", "taxas", "camunda");
        assertThat(conexoes.get(4).get("estado").asText()).isEqualTo("NAO_CONFIGURADA");
        assertThat(conexoes.get(0).get("estado").asText()).isEqualTo("NAO_CONFIGURADA");
        assertThat(conexoes.get(3).get("estado").asText()).isEqualTo("DESATIVADA");
        assertThat(resposta.getContentAsString()).doesNotContain(TOKEN_DE_ARQUIVOS);
        assertThat(json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8)).get("tempoLimiteDoTesteSegundos")
                .asInt()).as("CONEXAO_TESTE_TIMEOUT_SEGUNDOS, padrão 15").isEqualTo(15);
    }

    @Test
    void so_o_administrador() throws Exception {
        for (String usuario : List.of("operador", "gestor")) {
            assertThat(listar(usuario).getContentAsString()).contains("PERFIL_SEM_PERMISSAO");
            assertThat(testar(usuario, "siga").getStatus()).isEqualTo(403);
        }
    }

    @Test
    void teste_de_conexao_nao_configurada_ou_desconhecida() throws Exception {
        JsonNode siga = json.readTree(testar("admin", "siga").getContentAsString(StandardCharsets.UTF_8));

        assertThat(siga.get("sucesso").asBoolean()).isFalse();
        assertThat(siga.get("classificacao").asText()).isEqualTo("FALHA");
        assertThat(siga.get("tempoLimiteSegundos").asInt()).isEqualTo(15);
        assertThat(siga.has("codigoHttp")).as("sem resposta HTTP, o campo nem aparece").isFalse();
        assertThat(siga.get("mensagem").asText()).isEqualTo("Conexão não configurada.");
        assertThat(testar("admin", "desconhecida").getStatus()).isEqualTo(404);

        JsonNode camunda = json.readTree(testar("admin", "camunda").getContentAsString(StandardCharsets.UTF_8));
        assertThat(camunda.get("sucesso").asBoolean()).isFalse();
        assertThat(camunda.get("mensagem").asText()).isEqualTo("Conexão não configurada.");
        assertThat(camunda.has("codigoHttp")).isFalse();
    }
}
