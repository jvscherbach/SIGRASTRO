package br.gov.mt.sema.rastro.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Segurança do login (L1, bloco 6): enumeração (mesma resposta para login inexistente, desativado e senha errada, e no
 * bloqueio 429), vazamento (senha e hash nunca saem em resposta, log ou auditoria) e sessão (token anterior cai depois de
 * redefinição e de troca; logout continua valendo). Só senhas óbvias de teste e usuários criados aqui, com origens
 * ({@code X-Forwarded-For}) de documentação para não esbarrar no limite das demais classes de teste.
 */
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class SegurancaDoLoginHttpTest extends BaseHttpDeDocumentos {

    private static final String USUARIOS = "/api/configuracoes/usuarios";
    private static final String SENHA_INICIAL = "senha-de-teste-inicial-71";
    private static final String SENHA_REDEFINIDA = "senha-de-teste-redefinida-72";
    private static final String SENHA_TROCADA = "senha-de-teste-trocada-73";
    private static final String SENHA_ERRADA = "senha-errada-de-teste-74";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    private static String novoLogin() {
        return "login.seguro" + SEQUENCIA.incrementAndGet();
    }

    private static String origem() {
        return "192.0.2." + (100 + SEQUENCIA.incrementAndGet());
    }

    private MockHttpServletResponse entrar(String origem, String usuario, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", origem)
                .content(json.writeValueAsString(Map.of("usuario", usuario, "senha", senha)))).andReturn().getResponse();
    }

    private String tokenDe(String usuario, String senha) throws Exception {
        MockHttpServletResponse resposta = entrar(origem(), usuario, senha);
        assertThat(resposta.getStatus()).as("login de %s", usuario).isEqualTo(200);
        return "Bearer " + json.readTree(resposta.getContentAsString()).get("token").asText();
    }

    /** O Administrador inclui um usuário Gestor (sem CPF) com a senha inicial. */
    private String incluirGestor() throws Exception {
        String login = novoLogin();
        MockHttpServletResponse resposta = mvc.perform(post(USUARIOS).header(HttpHeaders.AUTHORIZATION, bearer("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("nome", "Pessoa de Teste", "login", login, "perfil", "OPERADOR_SIGILO",
                                "senha", SENHA_INICIAL)))).andReturn().getResponse();
        assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isEqualTo(201);
        return login;
    }

    private MockHttpServletResponse alterar(String login, Map<String, ?> corpo) throws Exception {
        return mvc.perform(patch(USUARIOS + "/" + login).header(HttpHeaders.AUTHORIZATION, bearer("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo))).andReturn().getResponse();
    }

    private MockHttpServletResponse me(String sessao) throws Exception {
        return mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, sessao)).andReturn().getResponse();
    }

    private MockHttpServletResponse trocar(String sessao, String atual, String nova) throws Exception {
        return mvc.perform(post("/api/auth/senha").header(HttpHeaders.AUTHORIZATION, sessao)
                .header("X-Forwarded-For", origem()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("senhaAtual", atual, "novaSenha", nova, "confirmacao", nova))))
                .andReturn().getResponse();
    }

    private static String texto(MockHttpServletResponse resposta) throws Exception {
        return resposta.getContentAsString(StandardCharsets.UTF_8);
    }

    /** Só o que identifica a recusa para o cliente (os ids de correlação e a hora mudam a cada resposta). */
    private Map<String, Object> essencia(MockHttpServletResponse resposta) throws Exception {
        JsonNode corpo = json.readTree(texto(resposta));
        Map<String, Object> essencia = new LinkedHashMap<>();
        essencia.put("status", resposta.getStatus());
        for (String campo : List.of("type", "title", "status", "detail", "errorCode")) {
            essencia.put(campo, corpo.has(campo) ? corpo.get(campo).asText() : null);
        }
        List<String> campos = new ArrayList<>();
        corpo.fieldNames().forEachRemaining(campos::add);
        essencia.put("campos", campos.stream().sorted().toList());
        return essencia;
    }

    // ------------------------------------------------------------------ enumeração

    @Test
    void usuario_inexistente_desativado_e_senha_errada_recebem_a_mesma_resposta() throws Exception {
        String desativado = incluirGestor();
        assertThat(alterar(desativado, Map.of("ativo", false)).getStatus()).isEqualTo(200);

        MockHttpServletResponse inexistente = entrar(origem(), "nunca.existiu.aqui", SENHA_INICIAL);
        MockHttpServletResponse inativo = entrar(origem(), desativado, SENHA_INICIAL);
        MockHttpServletResponse errada = entrar(origem(), "operador", SENHA_ERRADA);

        assertThat(inexistente.getStatus()).isEqualTo(401);
        assertThat(essencia(inativo)).isEqualTo(essencia(inexistente));
        assertThat(essencia(errada)).isEqualTo(essencia(inexistente));
        assertThat(essencia(inexistente)).containsEntry("detail", "Usuário ou senha inválidos.");
        for (MockHttpServletResponse resposta : List.of(inexistente, inativo, errada)) {
            assertThat(texto(resposta)).doesNotContain("nunca.existiu.aqui", desativado, "operador", SENHA_ERRADA,
                    SENHA_INICIAL, "inativ", "desativ", "inexistente");
        }
    }

    @Test
    void bloqueio_429_e_o_mesmo_para_login_existente_e_inexistente() throws Exception {
        String origemDoExistente = origem();
        String origemDoInexistente = origem();
        for (int i = 0; i < 3; i++) {
            assertThat(entrar(origemDoExistente, "operador", SENHA_ERRADA).getStatus()).isEqualTo(401);
            assertThat(entrar(origemDoInexistente, "nunca.existiu.bloqueio", SENHA_ERRADA).getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse existente = entrar(origemDoExistente, "operador", UsuariosDeTeste.senhaDe("operador"));
        MockHttpServletResponse inexistente = entrar(origemDoInexistente, "nunca.existiu.bloqueio", SENHA_ERRADA);

        assertThat(existente.getStatus()).isEqualTo(429);
        assertThat(essencia(inexistente)).isEqualTo(essencia(existente));
        assertThat(essencia(existente)).containsEntry("errorCode", "MUITAS_TENTATIVAS");
        assertThat(existente.getHeader("Retry-After")).isEqualTo(inexistente.getHeader("Retry-After"));
        assertThat(texto(existente)).doesNotContain("operador", UsuariosDeTeste.senhaDe("operador"));
        assertThat(texto(inexistente)).doesNotContain("nunca.existiu.bloqueio");
    }

    // ------------------------------------------------------------------ vazamento

    @Test
    void senha_e_hash_nunca_saem_em_resposta_log_ou_auditoria(CapturedOutput saida) throws Exception {
        List<MockHttpServletResponse> respostas = new ArrayList<>();
        String sessaoAdmin = bearer("admin");

        MockHttpServletResponse login = entrar(origem(), "gestor", UsuariosDeTeste.senhaDe("gestor"));
        respostas.add(login);
        respostas.add(entrar(origem(), "gestor", SENHA_ERRADA));
        respostas.add(me(sessaoAdmin));
        respostas.add(mvc.perform(get(USUARIOS).header(HttpHeaders.AUTHORIZATION, sessaoAdmin)).andReturn().getResponse());

        String login1 = novoLogin();
        MockHttpServletResponse incluido = mvc.perform(post(USUARIOS).header(HttpHeaders.AUTHORIZATION, sessaoAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("nome", "Pessoa de Teste", "login", login1, "perfil", "OPERADOR_SIGILO",
                                "senha", SENHA_INICIAL)))).andReturn().getResponse();
        assertThat(incluido.getStatus()).isEqualTo(201);
        respostas.add(incluido);
        MockHttpServletResponse redefinida = alterar(login1, Map.of("senha", SENHA_REDEFINIDA));
        assertThat(redefinida.getStatus()).isEqualTo(200);
        respostas.add(redefinida);
        respostas.add(alterar(login1, Map.of("senha", "curta")));

        String sessao = tokenDe(login1, SENHA_REDEFINIDA);
        respostas.add(me(sessao));
        respostas.add(trocar(sessao, SENHA_ERRADA, SENHA_TROCADA));
        respostas.add(trocar(sessao, SENHA_REDEFINIDA, "curta"));
        MockHttpServletResponse trocada = trocar(sessao, SENHA_REDEFINIDA, SENHA_TROCADA);
        assertThat(trocada.getStatus()).isEqualTo(204);
        respostas.add(trocada);

        respostas.add(mvc.perform(get("/api/auditoria/acessos?periodo=todo&tamanho=100")
                .header(HttpHeaders.AUTHORIZATION, sessaoAdmin)).andReturn().getResponse());
        respostas.add(mvc.perform(get("/api/auditoria/acessos/exportacao?periodo=todo")
                .header(HttpHeaders.AUTHORIZATION, sessaoAdmin)).andReturn().getResponse());

        List<String> proibidos = new ArrayList<>(List.of(SENHA_INICIAL, SENHA_REDEFINIDA, SENHA_TROCADA, SENHA_ERRADA,
                UsuariosDeTeste.SENHA_OPERADOR, UsuariosDeTeste.SENHA_ANALISTA, UsuariosDeTeste.SENHA_GESTOR,
                UsuariosDeTeste.SENHA_ADMIN, "pbkdf2", "senhaHash", "senha_hash"));
        for (MockHttpServletResponse resposta : respostas) {
            StringBuilder tudo = new StringBuilder(texto(resposta));
            for (String nome : resposta.getHeaderNames()) {
                tudo.append('\n').append(nome).append(": ").append(String.join(",", resposta.getHeaders(nome)));
            }
            for (String proibido : proibidos) {
                assertThat(tudo.toString()).as("resposta %s", resposta.getStatus()).doesNotContain(proibido);
            }
        }
        // O corpo do login e o de /me só têm token, tipo, validade e usuário; nenhum campo de senha.
        assertThat(json.readTree(texto(login)).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("token", "tipo", "expiraEm", "usuario");
        assertThat(json.readTree(texto(login)).get("usuario").fieldNames()).toIterable()
                .noneMatch(campo -> campo.toLowerCase().contains("senha") || campo.toLowerCase().contains("hash"));
        // Log do teste (saída do contexto Spring) nunca traz senha nem hash.
        String log = saida.getAll();
        for (String proibido : proibidos) {
            assertThat(log).as("log").doesNotContain(proibido);
        }
    }

    // ------------------------------------------------------------------ sessão

    @Test
    void redefinicao_pelo_administrador_derruba_o_token_anterior_e_a_senha_antiga() throws Exception {
        String login = incluirGestor();
        String antigo = tokenDe(login, SENHA_INICIAL);
        assertThat(me(antigo).getStatus()).isEqualTo(200);

        assertThat(alterar(login, Map.of("senha", SENHA_REDEFINIDA)).getStatus()).isEqualTo(200);

        assertThat(me(antigo).getStatus()).isEqualTo(401);
        assertThat(entrar(origem(), login, SENHA_INICIAL).getStatus()).isEqualTo(401);
        assertThat(me(tokenDe(login, SENHA_REDEFINIDA)).getStatus()).isEqualTo(200);
    }

    @Test
    void troca_da_propria_senha_derruba_as_outras_sessoes_e_mantem_a_atual() throws Exception {
        String login = incluirGestor();
        String atual = tokenDe(login, SENHA_INICIAL);
        String outra = tokenDe(login, SENHA_INICIAL);

        assertThat(trocar(atual, SENHA_INICIAL, SENHA_TROCADA).getStatus()).isEqualTo(204);

        assertThat(me(atual).getStatus()).as("sessão que trocou continua").isEqualTo(200);
        assertThat(me(outra).getStatus()).as("outra sessão cai").isEqualTo(401);
        assertThat(entrar(origem(), login, SENHA_INICIAL).getStatus()).isEqualTo(401);
        assertThat(entrar(origem(), login, SENHA_TROCADA).getStatus()).isEqualTo(200);
    }

    @Test
    void logout_encerra_a_sessao() throws Exception {
        String login = incluirGestor();
        String sessao = tokenDe(login, SENHA_INICIAL);

        assertThat(mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, sessao)).andReturn()
                .getResponse().getStatus()).isEqualTo(204);

        assertThat(me(sessao).getStatus()).isEqualTo(401);
    }

    @Test
    void sessao_de_usuario_desativado_deixa_de_valer() throws Exception {
        String login = incluirGestor();
        String sessao = tokenDe(login, SENHA_INICIAL);

        assertThat(alterar(login, Map.of("ativo", false)).getStatus()).isEqualTo(200);

        assertThat(me(sessao).getStatus()).isEqualTo(401);
    }
}
