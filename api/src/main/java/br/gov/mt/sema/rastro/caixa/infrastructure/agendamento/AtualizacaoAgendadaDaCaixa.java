package br.gov.mt.sema.rastro.caixa.infrastructure.agendamento;

import br.gov.mt.sema.rastro.caixa.application.AtualizarCaixa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Atualização periódica da caixa (S11). O agendador acorda a cada minuto e a regra do intervalo (parâmetro lido a cada ciclo)
 * fica em {@link AtualizarCaixa#atualizarSeVencida()}; antes da primeira requisição de um Analista, não faz nada.
 * AGENDAMENTO_ENABLED=false desliga.
 */
@Component
@ConditionalOnProperty(name = "rastro.agendamento.enabled", havingValue = "true", matchIfMissing = true)
class AtualizacaoAgendadaDaCaixa {

    private static final Logger log = LoggerFactory.getLogger(AtualizacaoAgendadaDaCaixa.class);

    private final AtualizarCaixa atualizar;

    AtualizacaoAgendadaDaCaixa(AtualizarCaixa atualizar) {
        this.atualizar = atualizar;
    }

    @Scheduled(fixedDelayString = "${rastro.agendamento.caixa:PT1M}", initialDelayString = "${rastro.agendamento.caixa:PT1M}")
    void verificar() {
        try {
            atualizar.atualizarSeVencida();
        } catch (RuntimeException e) {
            log.warn("Agendamento da caixa falhou: {}", e.getClass().getSimpleName());
        }
    }
}
