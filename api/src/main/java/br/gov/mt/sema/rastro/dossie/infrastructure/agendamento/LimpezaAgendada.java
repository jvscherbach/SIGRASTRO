package br.gov.mt.sema.rastro.dossie.infrastructure.agendamento;

import br.gov.mt.sema.rastro.dossie.application.LimparDossies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefas de limpeza do dossiê (docs/padroes/agendamento.md). Só chamam o caso de uso; uma exceção numa execução é
 * registrada e não derruba o agendador. AGENDAMENTO_ENABLED=false desliga todas.
 */
@Component
@ConditionalOnProperty(name = "rastro.agendamento.enabled", havingValue = "true", matchIfMissing = true)
class LimpezaAgendada {

    private static final Logger log = LoggerFactory.getLogger(LimpezaAgendada.class);

    private final LimparDossies limpar;

    LimpezaAgendada(LimparDossies limpar) {
        this.limpar = limpar;
    }

    /** Na subida a fila em memória está vazia: qualquer pasta que sobrou de uma execução anterior é lixo. */
    @EventListener(ApplicationReadyEvent.class)
    void aoSubir() {
        executar("orfãos na subida", limpar::apagarOrfaos);
    }

    @Scheduled(fixedDelayString = "${rastro.agendamento.limpeza-dossie:PT5M}", initialDelayString = "${rastro.agendamento.limpeza-dossie:PT5M}")
    void expirarVencidos() {
        executar("expiração", limpar::expirarVencidos);
    }

    @Scheduled(fixedDelayString = "${rastro.agendamento.limpeza-orfaos:PT1H}", initialDelayString = "${rastro.agendamento.limpeza-orfaos:PT1H}")
    void apagarOrfaos() {
        executar("orfãos", limpar::apagarOrfaos);
    }

    private void executar(String nome, java.util.function.IntSupplier tarefa) {
        long inicio = System.nanoTime();
        try {
            int tratados = tarefa.getAsInt();
            log.info("Limpeza de dossiês ({}): {} item(ns) em {} ms", nome, tratados, (System.nanoTime() - inicio) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("Limpeza de dossiês ({}) falhou: {}", nome, e.getMessage());
        }
    }
}
