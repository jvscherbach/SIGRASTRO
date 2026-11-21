package br.gov.mt.sema.rastro.tramitacao.infrastructure.memoria;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.rastro.tramitacao.application.port.CacheDaTramitacao;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import org.springframework.stereotype.Component;

/**
 * Cache da tramitação em memória (S8, bloco 7; D09: some ao reiniciar). Chave = id do requerimento + versão do catálogo.
 * Teto de {@value #MAXIMO_DE_ENTRADAS} entradas (uma tramitação pesa mais que uma posição): acima dele, saem as vencidas
 * e, se ainda não bastar, tudo (o custo é só reler o Camunda).
 */
@Component
class CacheDaTramitacaoEmMemoria implements CacheDaTramitacao {

    static final int MAXIMO_DE_ENTRADAS = 500;

    private record Chave(long idRequerimento, String versaoDoCatalogo) {}

    private record Entrada(TramitacaoDoRequerimento tramitacao, Instant vence) {}

    private final Map<Chave, Entrada> entradas = new ConcurrentHashMap<>();
    private final Clock relogio;

    CacheDaTramitacaoEmMemoria(Clock relogio) {
        this.relogio = relogio;
    }

    @Override
    public Optional<TramitacaoDoRequerimento> obter(long idRequerimento, String versaoDoCatalogo) {
        Chave chave = new Chave(idRequerimento, versaoDoCatalogo);
        Entrada entrada = entradas.get(chave);
        if (entrada == null) {
            return Optional.empty();
        }
        if (!relogio.instant().isBefore(entrada.vence())) {
            entradas.remove(chave, entrada);
            return Optional.empty();
        }
        return Optional.of(entrada.tramitacao());
    }

    @Override
    public void guardar(long idRequerimento, String versaoDoCatalogo, TramitacaoDoRequerimento tramitacao,
            Duration validade) {
        if (validade.isZero() || validade.isNegative()
                || tramitacao.estado() == TramitacaoDoRequerimento.Estado.INDISPONIVEL) {
            return;
        }
        Instant agora = relogio.instant();
        if (entradas.size() >= MAXIMO_DE_ENTRADAS) {
            entradas.values().removeIf(e -> !agora.isBefore(e.vence()));
            if (entradas.size() >= MAXIMO_DE_ENTRADAS) {
                entradas.clear();
            }
        }
        entradas.put(new Chave(idRequerimento, versaoDoCatalogo), new Entrada(tramitacao, agora.plus(validade)));
    }

    int tamanho() {
        return entradas.size();
    }
}
