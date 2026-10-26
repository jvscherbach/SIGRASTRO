package br.gov.mt.sema.vistas.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga o agendador do Spring. AGENDAMENTO_ENABLED=false desliga todas as tarefas agendadas. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "vistas.agendamento.enabled", havingValue = "true", matchIfMissing = true)
class AgendamentoConfig {
}
