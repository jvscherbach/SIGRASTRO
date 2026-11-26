package br.gov.mt.sema.rastro.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import br.gov.mt.sema.rastro.auditoria.application.ConsultarAuditoria;
import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.application.port.ConsultarEventos;
import br.gov.mt.sema.rastro.auditoria.application.port.RepositorioDeEventos;
import br.gov.mt.sema.rastro.auditoria.application.port.RetencaoDeEventos;
import br.gov.mt.sema.rastro.auditoria.domain.DocumentoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.rastro.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.UsuarioDoEvento;
import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Eventos são imutáveis (docs/11): nenhuma rota e nenhum método público alteram ou apagam. O único descarte é a retenção,
 * e o {@code ArchitectureTest} a prende à infraestrutura. A lista de métodos é fechada de propósito: um método novo
 * nestas interfaces precisa passar por aqui.
 */
class ImutabilidadeDaAuditoriaTest extends BaseHttpDeDocumentos {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeamentos;

    @Autowired
    RegistrarEvento registrar;

    @Autowired
    ConsultarEventos eventos;

    private static Set<String> publicos(Class<?> tipo) {
        return Arrays.stream(tipo.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic()).map(Method::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void toda_rota_da_auditoria_e_get() {
        Set<String> rotas = new TreeSet<>();
        for (RequestMappingInfo info : mapeamentos.getHandlerMethods().keySet()) {
            for (String caminho : info.getPatternValues()) {
                if (caminho.startsWith("/api/auditoria")) {
                    info.getMethodsCondition().getMethods().forEach(m -> rotas.add(m.name() + " " + caminho));
                    assertThat(info.getMethodsCondition().getMethods()).as(caminho).isNotEmpty();
                }
            }
        }

        assertThat(rotas).containsExactlyInAnyOrder("GET /api/auditoria/documentos", "GET /api/auditoria/acessos",
                "GET /api/auditoria/{grupo}/{id}", "GET /api/auditoria/{grupo}/exportacao");
    }

    @Test
    void escrita_na_auditoria_e_405_mesmo_para_o_administrador_e_nao_muda_nada() throws Exception {
        registrar.registrar(NovoEvento.de(TipoDeEvento.LOGIN, new UsuarioDoEvento("admin", "Administrador", "Administrador")).criar());
        EventoDeAuditoria alvo = eventos.doPeriodo(GrupoDeEvento.ACESSOS, null, null).findFirst().orElseThrow();
        long antes = eventos.doPeriodo(GrupoDeEvento.ACESSOS, null, null).count();
        String sessao = bearer("admin");
        long depoisDoLogin = antes + 1;

        for (HttpMethod metodo : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            for (String url : List.of("/api/auditoria/acessos", "/api/auditoria/acessos/" + alvo.id(),
                    "/api/auditoria/acessos/exportacao")) {
                assertThat(mvc.perform(request(metodo, url).header(HttpHeaders.AUTHORIZATION, sessao)).andReturn()
                        .getResponse().getStatus()).as(metodo + " " + url).isEqualTo(405);
            }
        }

        assertThat(eventos.doPeriodo(GrupoDeEvento.ACESSOS, null, null).count()).isEqualTo(depoisDoLogin);
        assertThat(eventos.porId(GrupoDeEvento.ACESSOS, alvo.id())).contains(alvo);
    }

    @Test
    void interfaces_e_caso_de_uso_nao_oferecem_alterar_nem_apagar() {
        assertThat(publicos(RepositorioDeEventos.class)).containsExactly("incluir");
        assertThat(publicos(ConsultarEventos.class)).containsExactly("doPeriodo", "porId");
        assertThat(publicos(RegistrarEvento.class)).containsExactly("registrar");
        assertThat(publicos(RetencaoDeEventos.class)).containsExactly("aplicar");
        assertThat(publicos(ConsultarAuditoria.class)).containsExactly("detalhar", "exportar", "listar");
    }

    @Test
    void evento_guardado_e_um_record_com_listas_que_nao_mudam() {
        registrar.registrar(NovoEvento.de(TipoDeEvento.CONSULTA, new UsuarioDoEvento("gestor", "Gestor", "Gestor"))
                .requerimento(101L, "2825/2025", "3331/2025").documentos(List.of(
                        new DocumentoDoEvento("Requerimento", "10", "Licença", "licenca.pdf", false, 1))).criar());
        EventoDeAuditoria evento = eventos.doPeriodo(GrupoDeEvento.DOCUMENTOS, null, null).findFirst().orElseThrow();

        assertThat(EventoDeAuditoria.class.isRecord()).isTrue();
        assertThatThrownBy(() -> evento.documentos().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> evento.alteracoes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
