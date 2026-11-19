package br.gov.mt.sema.rastro.auditoria.api;

import br.gov.mt.sema.rastro.auditoria.application.port.RequisicaoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.OrigemDoEvento;
import br.gov.mt.sema.rastro.shared.api.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class RequisicaoHttpDoEvento implements RequisicaoDoEvento {

    @Override
    public String origem() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest requisicao = atributos.getRequest();
            return OrigemDoEvento.de(requisicao.getHeader("X-Forwarded-For"), requisicao.getRemoteAddr());
        }
        return null;
    }

    @Override
    public String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
