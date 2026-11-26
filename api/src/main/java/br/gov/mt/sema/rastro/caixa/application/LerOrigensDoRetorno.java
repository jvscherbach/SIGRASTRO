package br.gov.mt.sema.rastro.caixa.application;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import br.gov.mt.sema.rastro.caixa.domain.OrigemDoRetorno;
import br.gov.mt.sema.rastro.tramitacao.application.LerTramitacao;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Lê de onde cada requerimento avisado veio (S11, bloco 5), pelo histórico da tramitação (S8). Só para os itens avisados, com
 * teto de leituras por atualização ({@value #TETO_DE_LEITURAS}) e o tempo que sobrou; o que passar do teto ou do tempo, ou
 * falhar, fica "Mudou de etapa" (nunca inventa). Cada leitura passa pelo cache, pelo single flight e pelo
 * {@code LimiteDeLeiturasDoCamunda} do {@link LerTramitacao}. Falha aqui nunca derruba a atualização da caixa.
 */
@Component
public class LerOrigensDoRetorno {

    private static final Logger log = LoggerFactory.getLogger(LerOrigensDoRetorno.class);

    /** Teto de requerimentos com o histórico lido por atualização. */
    public static final int TETO_DE_LEITURAS = 100;

    private final LongFunction<TramitacaoDoRequerimento> leitura;
    private final LongSupplier agoraEmNanos;

    @Autowired
    public LerOrigensDoRetorno(LerTramitacao tramitacao) {
        this(tramitacao::ler, System::nanoTime);
    }

    /** Para teste: leitura e relógio monotônico controlados. */
    LerOrigensDoRetorno(LongFunction<TramitacaoDoRequerimento> leitura, LongSupplier agoraEmNanos) {
        this.leitura = leitura;
        this.agoraEmNanos = agoraEmNanos;
    }

    /** A origem de cada id pedido (todos aparecem no resultado). */
    public Map<Long, OrigemDoRetorno> ler(Collection<Long> ids, Duration tempo) {
        long prazo = agoraEmNanos.getAsLong() + tempo.toNanos();
        Map<Long, OrigemDoRetorno> origens = new LinkedHashMap<>();
        int lidas = 0;
        for (Long id : ids) {
            if (origens.containsKey(id)) {
                continue;
            }
            if (lidas >= TETO_DE_LEITURAS || agoraEmNanos.getAsLong() >= prazo) {
                origens.put(id, OrigemDoRetorno.MUDOU_DE_ETAPA);
                continue;
            }
            lidas++;
            try {
                origens.put(id, OrigemDoRetorno.de(leitura.apply(id)));
            } catch (RuntimeException e) {
                log.warn("Origem do retorno não lida: {}", e.getClass().getSimpleName());
                origens.put(id, OrigemDoRetorno.MUDOU_DE_ETAPA);
            }
        }
        return origens;
    }
}
