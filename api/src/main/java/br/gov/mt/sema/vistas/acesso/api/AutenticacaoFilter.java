package br.gov.mt.sema.vistas.acesso.api;

import java.io.IOException;
import java.util.regex.Pattern;

import br.gov.mt.sema.vistas.acesso.application.ObterUsuarioAtual;
import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.shared.api.CorrelationIdFilter;
import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;
import br.gov.mt.sema.vistas.shared.api.error.ProblemDetails;
import br.gov.mt.sema.vistas.shared.application.exception.NaoAutenticadoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autenticação simulada do protótipo: exige Bearer em /api/**, exceto no login e no download de dossiê por ticket (o
 * ticket de uso único é conferido pelo caso de uso; com Bearer presente, ele também vale e precisa ser do mesmo usuário).
 */
@Component
@Order(CorrelationIdFilter.ORDEM + 10)
public class AutenticacaoFilter extends OncePerRequestFilter {

    static final String ATRIBUTO_USUARIO = AutenticacaoFilter.class.getName() + ".usuario";
    static final String ATRIBUTO_TOKEN = AutenticacaoFilter.class.getName() + ".token";

    private static final String PREFIXO_API = "/api/";
    private static final String LOGIN = "/api/auth/login";
    private static final String BEARER = "Bearer ";
    private static final Pattern DOWNLOAD_POR_TICKET =
            Pattern.compile("/api/dossies/[^/]+/arquivo");

    private final ObterUsuarioAtual obterUsuarioAtual;
    private final ObjectMapper json;

    public AutenticacaoFilter(ObterUsuarioAtual obterUsuarioAtual, ObjectMapper json) {
        this.obterUsuarioAtual = obterUsuarioAtual;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        if (!caminho.startsWith(PREFIXO_API)) {
            return true;
        }
        return HttpMethod.POST.matches(request.getMethod()) && LOGIN.equals(caminho);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extrairToken(request);
        if (token == null && downloadPorTicket(request)) {
            // O navegador baixa o dossiê grande direto, sem cabeçalho de sessão: quem confere é o ticket de uso único (D49).
            chain.doFilter(request, response);
            return;
        }
        try {
            Usuario usuario = obterUsuarioAtual.porToken(token);
            request.setAttribute(ATRIBUTO_USUARIO, usuario);
            request.setAttribute(ATRIBUTO_TOKEN, token);
        } catch (NaoAutenticadoException ex) {
            responderNaoAutenticado(request, response, ex.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }

    /** GET /api/dossies/{id}/arquivo com o parâmetro ticket. */
    static boolean downloadPorTicket(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return HttpMethod.GET.matches(request.getMethod()) && DOWNLOAD_POR_TICKET.matcher(caminho).matches()
                && request.getParameter("ticket") != null;
    }

    private static String extrairToken(HttpServletRequest request) {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho != null && cabecalho.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return cabecalho.substring(BEARER.length()).trim();
        }
        return null;
    }

    private void responderNaoAutenticado(HttpServletRequest request, HttpServletResponse response, String detalhe)
            throws IOException {
        response.setStatus(ErrorCode.NAO_AUTENTICADO.status().value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(),
                ProblemDetails.criar(ErrorCode.NAO_AUTENTICADO, detalhe, request));
    }
}
