package br.gov.mt.sema.rastro.documento.infrastructure.agendamento;

import br.gov.mt.sema.rastro.documento.application.CacheDeVisualizacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limpeza do cache do modal (S3), no mesmo intervalo da expiração dos dossiês. Esvazia na subida; uma exceção numa
 * execução é registrada e não derruba o agendador. AGENDAMENTO_ENABLED=false desliga.
 */
@Component
@ConditionalOnProperty(name = "rastro.agendamento.enabled", havingValue = "true", matchIfMissing = true)
class LimpezaDaVisualizacao {

    private static final Logger log = LoggerFactory.getLogger(LimpezaDaVisualizacao.class);

    private final CacheDeVisualizacao cache;

    LimpezaDaVisualizacao(CacheDeVisualizacao cache) {
        this.cache = cache;
    }

    @EventListener(ApplicationReadyEvent.class)
    void aoSubir() {
        executar("subida", cache::esvaziar);
    }

    @Scheduled(fixedDelayString = "${rastro.agendamento.limpeza-dossie:PT5M}", initialDelayString = "${rastro.agendamento.limpeza-dossie:PT5M}")
    void limparVencidos() {
        executar("vencidos", cache::limparVencidos);
    }

    private void executar(String nome, java.util.function.IntSupplier tarefa) {
        try {
            int apagados = tarefa.getAsInt();
            log.info("Limpeza do cache de visualização ({}): {} arquivo(s)", nome, apagados);
        } catch (RuntimeException e) {
            log.warn("Limpeza do cache de visualização ({}) falhou: {}", nome, e.getMessage());
        }
    }
}
