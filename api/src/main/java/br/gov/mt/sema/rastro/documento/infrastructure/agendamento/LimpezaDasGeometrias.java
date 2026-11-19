package br.gov.mt.sema.rastro.documento.infrastructure.agendamento;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.IntSupplier;

import br.gov.mt.sema.rastro.documento.application.BaixarGeometrias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rede de segurança das pastas temporárias do ZIP das geometrias (S4): cada download apaga a sua ao terminar; aqui só
 * sobra o que uma queda do serviço deixou. Esvazia na subida e, no intervalo dos órfãos, apaga pastas com mais de
 * {@value #HORAS} h. AGENDAMENTO_ENABLED=false desliga.
 */
@Component
@ConditionalOnProperty(name = "rastro.agendamento.enabled", havingValue = "true", matchIfMissing = true)
class LimpezaDasGeometrias {

    private static final Logger log = LoggerFactory.getLogger(LimpezaDasGeometrias.class);
    static final int HORAS = 6;

    private final BaixarGeometrias geometrias;
    private final Clock relogio;

    LimpezaDasGeometrias(BaixarGeometrias geometrias, Clock relogio) {
        this.geometrias = geometrias;
        this.relogio = relogio;
    }

    @EventListener(ApplicationReadyEvent.class)
    void aoSubir() {
        executar("subida", () -> geometrias.apagarTemporarios(null));
    }

    @Scheduled(fixedDelayString = "${rastro.agendamento.limpeza-orfaos:PT1H}", initialDelayString = "${rastro.agendamento.limpeza-orfaos:PT1H}")
    void apagarAntigas() {
        Instant limite = Instant.now(relogio).minus(Duration.ofHours(HORAS));
        executar("antigas", () -> geometrias.apagarTemporarios(limite));
    }

    private void executar(String nome, IntSupplier tarefa) {
        try {
            int apagadas = tarefa.getAsInt();
            log.info("Limpeza das pastas temporárias de geometrias ({}): {} pasta(s)", nome, apagadas);
        } catch (RuntimeException e) {
            log.warn("Limpeza das pastas temporárias de geometrias ({}) falhou: {}", nome, e.getMessage());
        }
    }
}
