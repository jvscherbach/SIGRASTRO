package br.gov.mt.sema.rastro.painel.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.LongSupplier;

import br.gov.mt.sema.rastro.painel.application.port.TarefasAtivasEmLote;
import br.gov.mt.sema.rastro.painel.domain.LeituraDasTarefas;
import br.gov.mt.sema.rastro.tramitacao.application.LimiteDeLeiturasDoCamunda;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Lê as tarefas ativas da base do painel em lotes de até {@value TarefasAtivasEmLote#MAXIMO_POR_LOTE} ids (S10, bloco 2):
 * uma consulta por lote, sem laço por linha, e <b>cada lote</b> passa pelo {@link LimiteDeLeiturasDoCamunda} (o mesmo da
 * posição e da tramitação), para a atualização do painel nunca somar mais leituras ao Camunda do que o teto da instância.
 *
 * <p>O tempo dado vale para a leitura inteira: cada lote espera pela vaga e consulta só com o que resta; acabou o tempo,
 * {@link TempoDoPainelEsgotado} e nenhum lote a mais é lido. Qualquer falha sobe: quem atualiza mantém o último instantâneo.
 */
@Component
public class LerTarefasEmLotes {

    private final TarefasAtivasEmLote tarefas;
    private final LimiteDeLeiturasDoCamunda limite;
    private final LongSupplier agoraEmNanos;

    @Autowired
    public LerTarefasEmLotes(TarefasAtivasEmLote tarefas, LimiteDeLeiturasDoCamunda limite) {
        this(tarefas, limite, System::nanoTime);
    }

    /** Para teste: relógio monotônico controlado. */
    LerTarefasEmLotes(TarefasAtivasEmLote tarefas, LimiteDeLeiturasDoCamunda limite, LongSupplier agoraEmNanos) {
        this.tarefas = tarefas;
        this.limite = limite;
        this.agoraEmNanos = agoraEmNanos;
    }

    /**
     * @param idsRequerimento  ids da base que estão na fila (repetidos contam uma vez)
     * @param chavesDosModelos chaves dos modelos do catálogo de etapas
     * @param tempoTotal       tempo para todos os lotes, incluindo a espera por vaga
     */
    public LeituraDasTarefas ler(Collection<Long> idsRequerimento, Collection<String> chavesDosModelos,
            Duration tempoTotal) {
        List<Long> distintos = List.copyOf(new LinkedHashSet<>(idsRequerimento));
        if (distintos.isEmpty()) {
            return LeituraDasTarefas.NENHUMA;
        }
        long prazo = agoraEmNanos.getAsLong() + tempoTotal.toNanos();
        List<LeituraDasTarefas> lidos = new ArrayList<>();
        for (int inicio = 0; inicio < distintos.size(); inicio += TarefasAtivasEmLote.MAXIMO_POR_LOTE) {
            List<Long> lote = distintos.subList(inicio,
                    Math.min(distintos.size(), inicio + TarefasAtivasEmLote.MAXIMO_POR_LOTE));
            Duration restante = Duration.ofNanos(prazo - agoraEmNanos.getAsLong());
            if (restante.isNegative() || restante.isZero()) {
                throw new TempoDoPainelEsgotado();
            }
            lidos.add(limite.executar(restante, () -> tarefas.ler(lote, chavesDosModelos, restante)));
        }
        return LeituraDasTarefas.juntar(lidos);
    }

    /** O tempo total da leitura acabou antes do último lote. */
    public static final class TempoDoPainelEsgotado extends RuntimeException {
        TempoDoPainelEsgotado() {
            super("tempo da leitura do painel esgotado", null, false, false);
        }
    }
}
