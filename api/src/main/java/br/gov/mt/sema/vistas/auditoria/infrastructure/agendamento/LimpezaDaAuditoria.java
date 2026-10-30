package br.gov.mt.sema.vistas.auditoria.infrastructure.agendamento;

import br.gov.mt.sema.vistas.auditoria.application.port.RetencaoDeEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retenção da auditoria (S5), no intervalo das limpezas de órfãos. AGENDAMENTO_ENABLED=false desliga. */
@Component
@ConditionalOnProperty(name = "vistas.agendamento.enabled", havingValue = "true", matchIfMissing = true)
class LimpezaDaAuditoria {

    private static final Logger log = LoggerFactory.getLogger(LimpezaDaAuditoria.class);

    private final RetencaoDeEventos retencao;

    LimpezaDaAuditoria(RetencaoDeEventos retencao) {
        this.retencao = retencao;
    }

    @Scheduled(fixedDelayString = "${vistas.agendamento.limpeza-orfaos:PT1H}", initialDelayString = "${vistas.agendamento.limpeza-orfaos:PT1H}")
    void aplicarRetencao() {
        try {
            log.info("Retenção da auditoria: {} evento(s) descartado(s)", retencao.aplicar());
        } catch (RuntimeException e) {
            log.warn("Retenção da auditoria falhou: {}", e.getClass().getSimpleName());
        }
    }
}
