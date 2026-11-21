package br.gov.mt.sema.rastro.tramitacao.infrastructure.memoria;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.rastro.tramitacao.application.port.CacheDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import org.springframework.stereotype.Component;

/**
 * Cache da posição em memória (D09: some ao reiniciar). Chave = id do requerimento + versão do catálogo, nada mais.
 * Teto de {@value #MAXIMO_DE_ENTRADAS} entradas: acima dele, saem as vencidas e, se ainda não bastar, tudo (o custo é só
 * reler o Camunda).
 */
@Component
class CacheDaPosicaoEmMemoria implements CacheDaPosicao {

    static final int MAXIMO_DE_ENTRADAS = 2_000;

    private record Chave(long idRequerimento, String versaoDoCatalogo) {}

    private record Entrada(PosicaoAtual posicao, Instant vence) {}

    private final Map<Chave, Entrada> entradas = new ConcurrentHashMap<>();
    private final Clock relogio;

    CacheDaPosicaoEmMemoria(Clock relogio) {
        this.relogio = relogio;
    }

    @Override
    public Optional<PosicaoAtual> obter(long idRequerimento, String versaoDoCatalogo) {
        Chave chave = new Chave(idRequerimento, versaoDoCatalogo);
        Entrada entrada = entradas.get(chave);
        if (entrada == null) {
            return Optional.empty();
        }
        if (!relogio.instant().isBefore(entrada.vence())) {
            entradas.remove(chave, entrada);
            return Optional.empty();
        }
        return Optional.of(entrada.posicao());
    }

    @Override
    public void guardar(long idRequerimento, String versaoDoCatalogo, PosicaoAtual posicao, Duration validade) {
        if (validade.isZero() || validade.isNegative()) {
            return;
        }
        Instant agora = relogio.instant();
        if (entradas.size() >= MAXIMO_DE_ENTRADAS) {
            entradas.values().removeIf(e -> !agora.isBefore(e.vence()));
            if (entradas.size() >= MAXIMO_DE_ENTRADAS) {
                entradas.clear();
            }
        }
        entradas.put(new Chave(idRequerimento, versaoDoCatalogo), new Entrada(posicao, agora.plus(validade)));
    }

    int tamanho() {
        return entradas.size();
    }
}
