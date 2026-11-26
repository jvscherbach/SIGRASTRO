package br.gov.mt.sema.rastro.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Usuário desativado ou rebaixado perde o acesso na requisição seguinte (S5, bloco 11), com sessão aberta, com ticket já
 * emitido (R53) e com pedido que ele mesmo gerou. O cadastro é alterado de verdade, por isso o contexto não é
 * reaproveitado. CPF fictício, de exemplo; o requerimento 303 é dele.
 */
@DirtiesContext
class AcessoRevogadoHttpTest extends BaseHttpDeDocumentos {

    private static final String USUARIOS = "/api/configuracoes/usuarios";
    private static final String CPF = "111.444.777-35";
    private static final String COMUM = "804328";
    private static final String SIGILOSO = "804329";
    private static final String TITULO_SIGILOSO = "Laudo reservado";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @BeforeEach
    void requerimentoDoUsuario() {
        when(siga.buscarPorId(303)).thenReturn(Optional.of(requerimento(303, CPF)));
    }

    private String incluir(String perfil) throws Exception {
        String login = "revogado.teste" + SEQUENCIA.incrementAndGet();
        assertThat(mvc.perform(post(USUARIOS).header(HttpHeaders.AUTHORIZATION, bearer("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("nome", "Pessoa de Teste", "login", login, "cpfCnpj", CPF, "perfil", perfil,
                                "senha", UsuariosDeTeste.senhaDe(login)))))
                .andReturn().getResponse().getStatus()).isEqualTo(201);
        return login;
    }

    private void alterar(String login, Map<String, ?> corpo) throws Exception {
        assertThat(mvc.perform(patch(USUARIOS + "/" + login).header(HttpHeaders.AUTHORIZATION, bearer("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse obter(String url, String sessao) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, sessao)).andReturn().getResponse();
    }

    private String gerar(String sessao, long requerimento, String... ids) throws Exception {
        List<Map<String, String>> documentos = java.util.Arrays.stream(ids)
                .map(id -> Map.of("origem", "DOCUMENTO_PROCESSO", "id", id)).toList();
        MockHttpServletResponse resposta = mvc.perform(post("/api/dossies").header(HttpHeaders.AUTHORIZATION, sessao)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idRequerimento", requerimento, "documentos", documentos))))
                .andReturn().getResponse();
        assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isEqualTo(202);
        return json.readTree(resposta.getContentAsString()).get("id").asText();
    }

    private String ticket(String sessao, String id) throws Exception {
        MockHttpServletResponse resposta = mvc.perform(post("/api/dossies/" + id + "/arquivo/ticket")
                .header(HttpHeaders.AUTHORIZATION, sessao)).andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(200);
        return json.readTree(resposta.getContentAsString()).get("ticket").asText();
    }

    private MockHttpServletResponse baixarPorTicket(String id, String ticket) throws Exception {
        return mvc.perform(get("/api/dossies/" + id + "/arquivo?disposicao=attachment&ticket=" + ticket))
                .andReturn().getResponse();
    }

    private static void erro(MockHttpServletResponse resposta, int status, String codigo) throws Exception {
        assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isEqualTo(status);
        assertThat(resposta.getContentAsString()).contains(codigo);
    }

    @Test
    void gestor_rebaixado_perde_pedido_com_sigiloso_ticket_e_auditoria_ampla_na_requisicao_seguinte() throws Exception {
        String login = incluir("OPERADOR_SIGILO");
        String sessao = bearer(login);
        String id = gerar(sessao, 303, COMUM, SIGILOSO);
        String ticket = ticket(sessao, id);
        assertThat(obter("/api/auditoria/documentos?somenteSigilosos=true", sessao).getStatus()).isEqualTo(200);
        assertThat(obter("/api/requerimentos/101", sessao).getStatus()).isEqualTo(200);

        alterar(login, Map.of("perfil", "OPERADOR"));

        erro(obter("/api/dossies/" + id, sessao), 404, "NAO_ENCONTRADO");
        erro(obter("/api/dossies/" + id + "/arquivo", sessao), 404, "NAO_ENCONTRADO");
        erro(baixarPorTicket(id, ticket), 403, "TICKET_INVALIDO");
        JsonNode dossies = json.readTree(obter("/api/dossies?tamanho=50", sessao).getContentAsString(StandardCharsets.UTF_8));
        assertThat(dossies.get("conteudo").findValuesAsText("id")).doesNotContain(id);
        erro(obter("/api/requerimentos/101", sessao), 403, "NAO_INTERESSADO");
        erro(obter("/api/auditoria/documentos?somenteSigilosos=true", sessao), 400, "PARAMETRO_INVALIDO");
        MockHttpServletResponse auditoria = obter("/api/auditoria/documentos?periodo=todo&tamanho=100", sessao);
        assertThat(auditoria.getContentAsString(StandardCharsets.UTF_8)).doesNotContain(TITULO_SIGILOSO, "laudo-reservado");
        erro(obter("/api/documentos/DOCUMENTO_PROCESSO/" + SIGILOSO + "/conteudo?requerimento=303", sessao), 403,
                "DOCUMENTO_SIGILOSO");
    }

    @Test
    void rebaixado_continua_com_o_pedido_sem_sigiloso_do_requerimento_em_que_e_interessado() throws Exception {
        String login = incluir("OPERADOR_SIGILO");
        String sessao = bearer(login);
        String id = gerar(sessao, 303, COMUM);
        String ticket = ticket(sessao, id);

        alterar(login, Map.of("perfil", "OPERADOR"));

        assertThat(obter("/api/dossies/" + id, sessao).getStatus()).isEqualTo(200);
        assertThat(baixarPorTicket(id, ticket).getStatus()).isEqualTo(200);
    }

    @Test
    void rebaixado_perde_o_pedido_de_requerimento_alheio() throws Exception {
        String login = incluir("OPERADOR_SIGILO");
        String sessao = bearer(login);
        String id = gerar(sessao, 101, COMUM);
        String ticket = ticket(sessao, id);

        alterar(login, Map.of("perfil", "OPERADOR"));

        erro(obter("/api/dossies/" + id, sessao), 404, "NAO_ENCONTRADO");
        erro(baixarPorTicket(id, ticket), 403, "TICKET_INVALIDO");
    }

    @Test
    void ticket_de_pedido_de_outro_usuario_cai_quando_quem_o_pediu_e_rebaixado() throws Exception {
        String login = incluir("OPERADOR_SIGILO");
        String id = gerar(bearer("gestor"), 101, COMUM, SIGILOSO);
        String ticket = ticket(bearer(login), id);

        alterar(login, Map.of("perfil", "OPERADOR"));

        erro(baixarPorTicket(id, ticket), 403, "TICKET_INVALIDO");
    }

    @Test
    void desativado_perde_sessao_e_ticket_na_requisicao_seguinte() throws Exception {
        String login = incluir("OPERADOR_SIGILO");
        String sessao = bearer(login);
        String id = gerar(sessao, 303, COMUM);
        String ticket = ticket(sessao, id);

        alterar(login, Map.of("ativo", false));

        erro(baixarPorTicket(id, ticket), 403, "TICKET_INVALIDO");
        for (String url : List.of("/api/auditoria/documentos", "/api/auditoria/documentos/exportacao", "/api/dossies/" + id,
                "/api/requerimentos/303")) {
            erro(obter(url, sessao), 401, "NAO_AUTENTICADO");
        }
    }

    @Test
    void administrador_rebaixado_perde_acessos_e_configuracoes_na_requisicao_seguinte() throws Exception {
        String login = incluir("ADMIN");
        String sessao = bearer(login);
        List<String> soDoAdministrador = List.of("/api/auditoria/acessos", "/api/auditoria/acessos/exportacao", USUARIOS,
                "/api/configuracoes/parametros", "/api/configuracoes/conexoes");
        for (String url : soDoAdministrador) {
            assertThat(obter(url, sessao).getStatus()).as(url).isEqualTo(200);
        }

        alterar(login, Map.of("perfil", "OPERADOR_SIGILO"));

        for (String url : soDoAdministrador) {
            erro(obter(url, sessao), 403, "PERFIL_SEM_PERMISSAO");
        }
        assertThat(obter("/api/auditoria/documentos", sessao).getStatus()).isEqualTo(200);
    }
}
