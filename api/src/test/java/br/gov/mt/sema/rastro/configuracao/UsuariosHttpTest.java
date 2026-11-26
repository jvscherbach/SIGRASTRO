package br.gov.mt.sema.rastro.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.rastro.auditoria.application.port.ConsultarEventos;
import br.gov.mt.sema.rastro.auditoria.domain.AlteracaoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.rastro.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Usuários e perfis (RF25): só Administrador, regras do cadastro, proteções, sessões abertas e evento {@code USUARIO}. O
 * cadastro é alterado de verdade, por isso o contexto não é reaproveitado. CPF e CNPJ fictícios, de exemplo.
 */
@DirtiesContext
class UsuariosHttpTest extends BaseHttpDeDocumentos {

    private static final String USUARIOS = "/api/configuracoes/usuarios";
    private static final String CPF = "111.444.777-35";
    private static final String CPF_DIGITOS = "11144477735";
    private static final String CNPJ = "11.222.333/0001-81";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired
    ConsultarEventos eventos;

    private static String novoLogin() {
        return "usuario.teste" + SEQUENCIA.incrementAndGet();
    }

    private MockHttpServletResponse listar(String usuario) throws Exception {
        return mvc.perform(get(USUARIOS).header(HttpHeaders.AUTHORIZATION, bearer(usuario))).andReturn().getResponse();
    }

    private MockHttpServletResponse incluir(String usuario, Map<String, ?> corpo) throws Exception {
        Map<String, Object> comSenha = new HashMap<>(corpo);
        comSenha.putIfAbsent("senha", UsuariosDeTeste.senhaDe(String.valueOf(corpo.get("login")).trim().toLowerCase()));
        return mvc.perform(post(USUARIOS).header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(comSenha)))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse alterar(String usuario, String id, Map<String, ?> corpo) throws Exception {
        return mvc.perform(patch(USUARIOS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)))
                .andReturn().getResponse();
    }

    private String incluirOperador() throws Exception {
        String login = novoLogin();
        assertThat(incluir("admin", Map.of("nome", "Usuária de Teste", "login", login, "cpfCnpj", CPF,
                "perfil", "OPERADOR")).getStatus()).isEqualTo(201);
        return login;
    }

    private String incluirGestor() throws Exception {
        String login = novoLogin();
        assertThat(incluir("admin", Map.of("nome", "Gestora de Teste", "login", login, "perfil", "OPERADOR_SIGILO"))
                .getStatus()).isEqualTo(201);
        return login;
    }

    private List<EventoDeAuditoria> eventosDe(String login) {
        return new ArrayList<>(eventos.doPeriodo(GrupoDeEvento.ACESSOS, null, null)
                .filter(e -> e.tipo() == TipoDeEvento.USUARIO && login.equals(e.detalhe())).toList());
    }

    private JsonNode noUsuario(MockHttpServletResponse resposta, String login) throws Exception {
        for (JsonNode usuario : json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8)).get("conteudo")) {
            if (usuario.get("login").asText().equals(login)) {
                return usuario;
            }
        }
        throw new AssertionError("usuário fora da lista: " + login);
    }

    private static void erro(MockHttpServletResponse resposta, int status, String codigo) throws Exception {
        assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isEqualTo(status);
        assertThat(resposta.getContentAsString()).contains(codigo);
    }

    @Test
    void so_o_administrador() throws Exception {
        Map<String, Object> corpo = Map.of("nome", "X", "login", novoLogin(), "perfil", "ADMIN");
        for (String usuario : List.of("operador", "gestor")) {
            erro(listar(usuario), 403, "PERFIL_SEM_PERMISSAO");
            erro(incluir(usuario, corpo), 403, "PERFIL_SEM_PERMISSAO");
            erro(alterar(usuario, "gestor", Map.of("ativo", false)), 403, "PERFIL_SEM_PERMISSAO");
        }
        assertThat(listar("admin").getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get(USUARIOS)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void lista_com_campos_e_cpf_cnpj_mascarado() throws Exception {
        MockHttpServletResponse resposta = listar("admin");

        JsonNode operador = noUsuario(resposta, "operador");
        assertThat(operador.get("id").asText()).isEqualTo("operador");
        assertThat(operador.get("perfil").asText()).isEqualTo("OPERADOR");
        assertThat(operador.get("perfilRotulo").asText()).isEqualTo("Operador");
        assertThat(operador.get("ativo").asBoolean()).isTrue();
        assertThat(operador.get("cpfCnpj").asText()).isEqualTo("**.222.333/****-**");
        assertThat(noUsuario(resposta, "gestor").get("cpfCnpj").isNull()).isTrue();
        assertThat(noUsuario(resposta, "admin").get("ultimoAcesso").isNull()).isFalse();
        assertThat(resposta.getContentAsString()).doesNotContain("11222333000181", CNPJ_DO_OPERADOR);
    }

    @Test
    void inclusao_valida_normaliza_o_login_guarda_mascarado_e_gera_evento_sem_documento() throws Exception {
        String login = novoLogin();

        MockHttpServletResponse resposta = incluir("admin", Map.of("nome", " Usuária de Teste ",
                "login", "  " + login.toUpperCase() + " ", "cpfCnpj", CPF, "perfil", "OPERADOR"));

        assertThat(resposta.getStatus()).isEqualTo(201);
        JsonNode criado = json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8));
        assertThat(criado.get("login").asText()).isEqualTo(login);
        assertThat(criado.get("nome").asText()).isEqualTo("Usuária de Teste");
        assertThat(criado.get("cpfCnpj").asText()).isEqualTo("***.444.777-**");
        assertThat(String.join(",", resposta.getHeaderNames()) + resposta.getContentAsString())
                .doesNotContain(CPF, CPF_DIGITOS);
        assertThat(eventosDe(login)).hasSize(1);
        EventoDeAuditoria evento = eventosDe(login).get(0);
        assertThat(evento.usuario().login()).isEqualTo("admin");
        assertThat(evento.alteracoes()).extracting(AlteracaoDoEvento::item)
                .containsExactly("Inclusão", "Senha", "Nome", "Login", "Perfil", "Situação");
        assertThat(evento.alteracoes().get(0).valorNovo()).isEqualTo("incluído");
        assertThat(evento.toString()).doesNotContain(CPF, CPF_DIGITOS, "444.777");
    }

    @Test
    void cnpj_valido_e_gestor_sem_documento_entram() throws Exception {
        assertThat(incluir("admin", Map.of("nome", "Empresa", "login", novoLogin(), "cpfCnpj", CNPJ,
                "perfil", "OPERADOR")).getStatus()).isEqualTo(201);
        incluirGestor();
    }

    @Test
    void inclusao_invalida() throws Exception {
        Map<String, Object> valido = new HashMap<>(Map.of("nome", "Teste", "login", novoLogin(), "cpfCnpj", CPF,
                "perfil", "OPERADOR"));

        for (String[] caso : List.of(new String[] {"nome", " "}, new String[] {"login", "a b"},
                new String[] {"cpfCnpj", "111.444.777-36"}, new String[] {"cpfCnpj", ""}, new String[] {"perfil", "CHEFE"})) {
            Map<String, Object> corpo = new HashMap<>(valido);
            corpo.put(caso[0], caso[1]);
            MockHttpServletResponse resposta = incluir("admin", corpo);
            erro(resposta, 400, "PARAMETRO_INVALIDO");
            assertThat(resposta.getContentAsString()).contains(caso[0]).doesNotContain("111.444.777-36");
        }
        assertThat(eventosDe(valido.get("login").toString())).isEmpty();
    }

    @Test
    void login_repetido() throws Exception {
        String login = incluirOperador();

        erro(incluir("admin", Map.of("nome", "Outro", "login", login, "cpfCnpj", CPF, "perfil", "OPERADOR")), 409,
                "LOGIN_JA_EXISTE");
        erro(incluir("admin", Map.of("nome", "Outro", "login", "OPERADOR", "cpfCnpj", CPF, "perfil", "OPERADOR")),
                409, "LOGIN_JA_EXISTE");
        assertThat(eventosDe(login)).hasSize(1);
    }

    @Test
    void usuario_incluido_entra_com_a_senha_definida_na_inclusao() throws Exception {
        assertThat(mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(incluirOperador())))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void alteracao_gera_um_evento_com_antes_e_depois() throws Exception {
        String login = incluirGestor();

        MockHttpServletResponse resposta = alterar("admin", login, Map.of("perfil", "ADMIN", "ativo", false));

        assertThat(resposta.getStatus()).isEqualTo(200);
        assertThat(eventosDe(login)).hasSize(2);
        EventoDeAuditoria evento = eventosDe(login).get(0);
        assertThat(evento.alteracoes()).containsExactly(new AlteracaoDoEvento("Perfil", "Gestor", "Administrador"),
                new AlteracaoDoEvento("Situação", "Ativo", "Inativo"));
    }

    @Test
    void sem_mudanca_real_nao_gera_evento() throws Exception {
        String login = incluirGestor();

        assertThat(alterar("admin", login, Map.of("perfil", "OPERADOR_SIGILO", "ativo", true)).getStatus()).isEqualTo(200);

        assertThat(eventosDe(login)).hasSize(1);
    }

    @Test
    void alterar_usuario_inexistente_ou_com_dado_invalido() throws Exception {
        erro(alterar("admin", "nao.existe", Map.of("ativo", false)), 404, "NAO_ENCONTRADO");
        erro(alterar("admin", incluirGestor(), Map.of("perfil", "CHEFE")), 400, "PARAMETRO_INVALIDO");
    }

    @Test
    void operador_precisa_de_documento_mesmo_na_alteracao() throws Exception {
        erro(alterar("admin", incluirGestor(), Map.of("perfil", "OPERADOR")), 400, "PARAMETRO_INVALIDO");
    }

    @Test
    void nao_rebaixa_o_ultimo_administrador() throws Exception {
        erro(alterar("admin", "admin", Map.of("perfil", "OPERADOR_SIGILO")), 409, "ULTIMO_ADMINISTRADOR");
    }

    @Test
    void nao_desativa_o_proprio_usuario() throws Exception {
        String segundo = incluirGestor();
        alterar("admin", segundo, Map.of("perfil", "ADMIN"));

        erro(alterar("admin", "admin", Map.of("ativo", false)), 409, "PROPRIO_USUARIO");

        alterar("admin", segundo, Map.of("ativo", false));
    }

    @Test
    void com_dois_administradores_um_pode_se_rebaixar() throws Exception {
        String segundo = incluirGestor();
        alterar("admin", segundo, Map.of("perfil", "ADMIN"));

        assertThat(alterar(segundo, segundo, Map.of("perfil", "OPERADOR_SIGILO")).getStatus()).isEqualTo(200);

        erro(alterar("admin", "admin", Map.of("perfil", "OPERADOR_SIGILO")), 409, "ULTIMO_ADMINISTRADOR");
    }

    @Test
    void usuario_inativo_nao_entra_com_a_mensagem_de_credencial_invalida() throws Exception {
        String login = incluirGestor();
        alterar("admin", login, Map.of("ativo", false));

        MockHttpServletResponse inativo = entrar(login, UsuariosDeTeste.senhaDe(login));
        MockHttpServletResponse senhaErrada = entrar("gestor", "senha-errada");

        assertThat(inativo.getStatus()).isEqualTo(401);
        assertThat(detalhe(inativo)).isEqualTo(detalhe(senhaErrada));
        assertThat(inativo.getContentAsString()).doesNotContain("inativo");
    }

    private MockHttpServletResponse entrar(String login, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("usuario", login, "senha", senha)))).andReturn().getResponse();
    }

    private String detalhe(MockHttpServletResponse resposta) throws Exception {
        return json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8)).get("detail").asText();
    }

    @Test
    void sessao_aberta_passa_a_valer_com_o_perfil_novo_na_proxima_requisicao() throws Exception {
        String login = incluirGestor();
        String sessao = bearer(login);
        assertThat(mvc.perform(get("/api/auditoria/acessos").header(HttpHeaders.AUTHORIZATION, sessao))
                .andReturn().getResponse().getStatus()).isEqualTo(403);

        alterar("admin", login, Map.of("perfil", "ADMIN"));

        assertThat(mvc.perform(get("/api/auditoria/acessos").header(HttpHeaders.AUTHORIZATION, sessao))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        alterar("admin", login, Map.of("ativo", false));
    }

    @Test
    void sessao_aberta_deixa_de_valer_ao_desativar() throws Exception {
        String login = incluirGestor();
        String sessao = bearer(login);

        alterar("admin", login, Map.of("ativo", false));

        assertThat(mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, sessao))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void reativar_devolve_o_acesso_e_o_historico_fica() throws Exception {
        String login = incluirGestor();
        alterar("admin", login, Map.of("ativo", false));

        assertThat(alterar("admin", login, Map.of("ativo", true)).getStatus()).isEqualTo(200);

        assertThat(entrar(login, UsuariosDeTeste.senhaDe(login)).getStatus()).isEqualTo(200);
        assertThat(eventosDe(login)).hasSize(3);
    }
}
