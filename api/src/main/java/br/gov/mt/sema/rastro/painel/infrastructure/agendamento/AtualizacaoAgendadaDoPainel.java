package br.gov.mt.sema.rastro.painel.infrastructure.agendamento;

import br.gov.mt.sema.rastro.painel.application.AtualizarPainel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Atualização periódica do painel (S10). O agendador acorda a cada minuto e a regra do intervalo (parâmetro lido a cada
 * ciclo) fica em {@link AtualizarPainel#atualizarSeVencida()}; antes da primeira requisição ao painel, não faz nada.
 * AGENDAMENTO_ENABLED=false desliga.
 */
@Component
@ConditionalOnProperty(name = "rastro.agendamento.enabled", havingValue = "true", matchIfMissing = true)
class AtualizacaoAgendadaDoPainel {

    private static final Logger log = LoggerFactory.getLogger(AtualizacaoAgendadaDoPainel.class);

    private final AtualizarPainel atualizar;

    AtualizacaoAgendadaDoPainel(AtualizarPainel atualizar) {
        this.atualizar = atualizar;
    }

    @Scheduled(fixedDelayString = "${rastro.agendamento.painel:PT1M}", initialDelayString = "${rastro.agendamento.painel:PT1M}")
    void verificar() {
        try {
            atualizar.atualizarSeVencida();
        } catch (RuntimeException e) {
            log.warn("Agendamento do painel falhou: {}", e.getClass().getSimpleName());
        }
    }
}
