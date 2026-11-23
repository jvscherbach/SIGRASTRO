package br.gov.mt.sema.rastro.painel.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

/**
 * Atualização manual do painel (S10, bloco 4), limitada por um intervalo mínimo desde o início da última atualização
 * (qualquer uma). Já havendo uma em curso, a solicitação é aceita e não dispara outra (single flight).
 */
@Service
public class SolicitarAtualizacao {

    /** {@code tentarEmSegundos} só vale quando recusada. */
    public record Resultado(boolean aceita, long tentarEmSegundos) {
    }

    private final AtualizarPainel atualizar;
    private final ParametrosDoPainel parametros;
    private final Clock relogio;

    public SolicitarAtualizacao(AtualizarPainel atualizar, ParametrosDoPainel parametros, Clock relogio) {
        this.atualizar = atualizar;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    public Resultado solicitar() {
        if (atualizar.emAndamento()) {
            return new Resultado(true, 0);
        }
        Instant agora = relogio.instant();
        var ultimo = atualizar.ultimoInicio();
        if (ultimo.isPresent()) {
            Instant liberaEm = ultimo.get().plusSeconds(parametros.intervaloMinimoManualSegundos());
            if (agora.isBefore(liberaEm)) {
                long falta = (long) Math.ceil(Duration.between(agora, liberaEm).toMillis() / 1000.0);
                return new Resultado(false, Math.max(1, falta));
            }
        }
        atualizar.iniciarEmSegundoPlano();
        return new Resultado(true, 0);
    }
}
