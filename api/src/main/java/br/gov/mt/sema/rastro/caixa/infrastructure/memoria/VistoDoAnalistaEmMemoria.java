package br.gov.mt.sema.rastro.caixa.infrastructure.memoria;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import br.gov.mt.sema.rastro.caixa.application.port.VistoDoAnalista;
import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.EstadoDePosse;
import org.springframework.stereotype.Repository;

/**
 * O visto em memória (S11, bloco 5). Some ao reiniciar: a tela e o roteiro dizem isso. A chave do analista também muda a
 * cada subida (sal sorteado), então nada daqui serve fora desta execução.
 */
// H1: persistência — o visto vai para o schema próprio, atrás de VistoDoAnalista (a chave passa a ser estável).
@Repository
class VistoDoAnalistaEmMemoria implements VistoDoAnalista {

    private final ConcurrentHashMap<ChaveDoResponsavel, Map<Long, EstadoDePosse>> vistos = new ConcurrentHashMap<>();

    @Override
    public Optional<Map<Long, EstadoDePosse>> ultimo(ChaveDoResponsavel analista) {
        return Optional.ofNullable(vistos.get(analista));
    }

    @Override
    public boolean gravarSeAusente(ChaveDoResponsavel analista, Map<Long, EstadoDePosse> estados) {
        return vistos.putIfAbsent(analista, Map.copyOf(estados)) == null;
    }

    @Override
    public void atualizar(ChaveDoResponsavel analista, UnaryOperator<Map<Long, EstadoDePosse>> regra) {
        vistos.compute(analista, (k, atual) -> Map.copyOf(regra.apply(atual == null ? Map.of() : atual)));
    }
}
