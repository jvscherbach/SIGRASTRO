package br.gov.mt.sema.vistas.shared.api.error;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import br.gov.mt.sema.vistas.shared.api.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Monta o ProblemDetail padrão do projeto; usado pelo handler e pelos filtros. */
public final class ProblemDetails {

    public static final ZoneId FUSO = ZoneId.of("America/Cuiaba");

    private ProblemDetails() {
    }

    public static ProblemDetail criar(ErrorCode codigo, String detalhe, String path) {
        return criar(codigo.status(), codigo, codigo.tituloPadrao(), detalhe, path, null);
    }

    public static ProblemDetail criar(ErrorCode codigo, String detalhe, HttpServletRequest request) {
        return criar(codigo, detalhe, request.getRequestURI());
    }

    public static ProblemDetail criar(HttpStatusCode status, ErrorCode codigo, String titulo, String detalhe,
            String path, List<CampoInvalido> campos) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setProperty("errorCode", codigo.name());
        problema.setProperty("timestamp", OffsetDateTime.now(FUSO).toString());
        problema.setProperty("path", path);
        problema.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        if (campos != null && !campos.isEmpty()) {
            problema.setProperty("campos", campos);
        }
        return problema;
    }
}
