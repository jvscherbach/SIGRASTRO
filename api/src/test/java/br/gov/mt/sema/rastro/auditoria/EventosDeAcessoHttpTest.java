package br.gov.mt.sema.rastro.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.auditoria.application.port.RepositorioDeEventos;
import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.rastro.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Eventos de acesso (S5, bloco 6): {@code LOGIN} em cada entrada, {@code PERFIL} quando a entrada troca o perfil de uma
 * sessão aberta; login recusado não registra e a resposta do login não muda.
 */
class EventosDeAcessoHttpTest extends BaseHttpDeDocumentos {

    @MockitoSpyBean
    RepositorioDeEventos eventos;

    private long ultimoId;

    @BeforeEach
    void marcarInicio() {
        reset(eventos);
        ultimoId = todos().stream().mapToLong(EventoDeAuditoria::id).max().orElse(0);
    }

    private List<EventoDeAuditoria> todos() {
        return new ArrayList<>(eventos.doPeriodo(GrupoDeEvento.ACESSOS, null, null).toList());
    }

    private List<EventoDeAuditoria> novos() {
        return todos().stream().filter(e -> e.id() > ultimoId).sorted(Comparator.comparingLong(EventoDeAuditoria::id)).toList();
    }

    private MockHttpServletResponse entrar(String usuario, String senha, String autorizacao) throws Exception {
        var pedido = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.9, 198.51.100.7")
                .content(json.writeValueAsString(Map.of("usuario", usuario, "senha", senha)));
        if (autorizacao != null) {
            pedido.header(HttpHeaders.AUTHORIZATION, autorizacao);
        }
        return mvc.perform(pedido).andReturn().getResponse();
    }

    @Test
    void login_bem_sucedido_registra_um_LOGIN_com_retrato_e_origem() throws Exception {
        MockHttpServletResponse resposta = entrar("operador.demo", "senha-de-teste", null);

        assertThat(resposta.getStatus()).isEqualTo(200);
        assertThat(novos()).hasSize(1);
        EventoDeAuditoria evento = novos().get(0);
        assertThat(evento.tipo()).isEqualTo(TipoDeEvento.LOGIN);
        assertThat(evento.usuario().login()).isEqualTo("operador.demo");
        assertThat(evento.usuario().nome()).isEqualTo("Operador Demo");
        assertThat(evento.usuario().perfil()).isEqualTo("Operador");
        assertThat(evento.origem()).isEqualTo("198.51.100.7");
        assertThat(evento.correlationId()).isNotBlank();
        assertThat(evento.idRequerimento()).isNull();
    }

    @Test
    void a_resposta_do_login_nao_muda_e_nao_traz_nada_da_auditoria() throws Exception {
        MockHttpServletResponse resposta = entrar("gestor.demo", "senha-de-teste", null);

        var corpo = json.readTree(resposta.getContentAsString());
        assertThat(corpo.fieldNames()).toIterable().containsExactlyInAnyOrder("token", "tipo", "expiraEm", "usuario");
        assertThat(corpo.get("tipo").asText()).isEqualTo("Bearer");
    }

    @Test
    void login_recusado_nao_registra_evento() throws Exception {
        MockHttpServletResponse resposta = entrar("operador.demo", "senha-errada", null);

        assertThat(resposta.getStatus()).isEqualTo(401);
        assertThat(novos()).isEmpty();
    }

    @Test
    void troca_para_outro_perfil_com_sessao_aberta_registra_LOGIN_e_PERFIL_em_nome_de_quem_entrou() throws Exception {
        String sessaoDoOperador = bearer("operador.demo");
        long antes = todos().stream().mapToLong(EventoDeAuditoria::id).max().orElse(0);

        MockHttpServletResponse resposta = entrar("admin.demo", "senha-de-teste", sessaoDoOperador);

        assertThat(resposta.getStatus()).isEqualTo(200);
        List<EventoDeAuditoria> depois = todos().stream().filter(e -> e.id() > antes)
                .sorted(Comparator.comparingLong(EventoDeAuditoria::id)).toList();
        assertThat(depois).extracting(EventoDeAuditoria::tipo).containsExactly(TipoDeEvento.LOGIN, TipoDeEvento.PERFIL);
        assertThat(depois).allSatisfy(e -> assertThat(e.usuario().login()).isEqualTo("admin.demo"));
        assertThat(depois.get(1).detalhe()).isEqualTo("Perfil novo: Administrador");
    }

    @Test
    void entrar_de_novo_com_o_mesmo_perfil_ou_com_token_invalido_so_registra_LOGIN() throws Exception {
        String sessao = bearer("gestor.demo");
        long antes = todos().stream().mapToLong(EventoDeAuditoria::id).max().orElse(0);

        entrar("gestor.demo", "senha-de-teste", sessao);
        entrar("operador.demo", "senha-de-teste", "Bearer token-que-nao-existe");

        List<EventoDeAuditoria> depois = todos().stream().filter(e -> e.id() > antes).toList();
        assertThat(depois).extracting(EventoDeAuditoria::tipo).containsOnly(TipoDeEvento.LOGIN).hasSize(2);
    }

    @Test
    void falha_do_repositorio_de_auditoria_nao_derruba_o_login() throws Exception {
        doThrow(new IllegalStateException("falha simulada")).when(eventos).incluir(org.mockito.ArgumentMatchers.any(NovoEvento.class));

        MockHttpServletResponse resposta = entrar("operador.demo", "senha-de-teste", null);

        assertThat(resposta.getStatus()).isEqualTo(200);
        assertThat(json.readTree(resposta.getContentAsString()).get("token").asText()).isNotBlank();
    }
}
