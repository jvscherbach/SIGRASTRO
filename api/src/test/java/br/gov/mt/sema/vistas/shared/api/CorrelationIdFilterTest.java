package br.gov.mt.sema.vistas.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filtro = new CorrelationIdFilter();

    @Test
    void aceitaIdValidoDoCabecalhoEColocaNoMdcDuranteAChamada() throws Exception {
        AtomicReference<String> noMdc = new AtomicReference<>();

        MockHttpServletResponse resposta = executar("abc-123_X.9", noMdc);

        assertThat(resposta.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123_X.9");
        assertThat(noMdc.get()).isEqualTo("abc-123_X.9");
    }

    @Test
    void rejeitaIdComCaracteresInvalidosEGeraUuid() throws Exception {
        MockHttpServletResponse resposta = executar("invalido com espaco;<script>", new AtomicReference<>());

        String id = resposta.getHeader(CorrelationIdFilter.HEADER);
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void rejeitaIdComMaisDe64Caracteres() throws Exception {
        MockHttpServletResponse resposta = executar("a".repeat(65), new AtomicReference<>());

        assertThat(resposta.getHeader(CorrelationIdFilter.HEADER)).hasSize(36);
    }

    @Test
    void aceitaIdComExatamente64Caracteres() throws Exception {
        String id = "a".repeat(64);

        assertThat(executar(id, new AtomicReference<>()).getHeader(CorrelationIdFilter.HEADER)).isEqualTo(id);
    }

    @Test
    void geraUuidQuandoAusenteELimpaOMdcAoFinal() throws Exception {
        AtomicReference<String> noMdc = new AtomicReference<>();

        MockHttpServletResponse resposta = executar(null, noMdc);

        String id = resposta.getHeader(CorrelationIdFilter.HEADER);
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(noMdc.get()).isEqualTo(id);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private MockHttpServletResponse executar(String cabecalho, AtomicReference<String> noMdc) throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/api/qualquer");
        if (cabecalho != null) {
            requisicao.addHeader(CorrelationIdFilter.HEADER, cabecalho);
        }
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockFilterChain cadeia = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                noMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY));
                super.doFilter(req, res);
            }
        };
        filtro.doFilter(requisicao, resposta, cadeia);
        return resposta;
    }
}
