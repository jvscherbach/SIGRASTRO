package br.gov.mt.sema.rastro.caixa.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import br.gov.mt.sema.rastro.caixa.application.port.PosseDoAnalistaEmLote;
import br.gov.mt.sema.rastro.caixa.domain.AnalistaParaLeitura;
import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.LeituraDaPosse;
import br.gov.mt.sema.rastro.tramitacao.application.LimiteDeLeiturasDoCamunda;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Lê a posse dos analistas em lotes de até {@value PosseDoAnalistaEmLote#MAXIMO_POR_LOTE} (S11, bloco 4): para cada lote de
 * analistas, uma consulta de candidatos e, para cada lote de até 500 candidatos, uma consulta de detalhe; sem laço por
 * linha, e <b>cada consulta</b> ocupa uma vaga do {@link LimiteDeLeiturasDoCamunda} (o mesmo da posição, da tramitação e do
 * painel), para a atualização da caixa nunca somar mais leituras ao Camunda do que o teto da instância.
 *
 * <p>O tempo dado vale para a leitura inteira (incluindo a espera por vaga): acabou, {@link TempoDaCaixaEsgotado} e nenhum
 * lote a mais é lido. O teto de requerimentos é o do painel (quem chama o passa); passou, a leitura volta marcada como
 * incompleta. Qualquer falha sobe: quem atualiza mantém o último instantâneo bom.
 */
@Component
public class LerPosseEmLotes {

    private final PosseDoAnalistaEmLote posse;
    private final LimiteDeLeiturasDoCamunda limite;
    private final LongSupplier agoraEmNanos;

    @Autowired
    public LerPosseEmLotes(PosseDoAnalistaEmLote posse, LimiteDeLeiturasDoCamunda limite) {
        this(posse, limite, System::nanoTime);
    }

    /** Para teste: relógio monotônico controlado. */
    LerPosseEmLotes(PosseDoAnalistaEmLote posse, LimiteDeLeiturasDoCamunda limite, LongSupplier agoraEmNanos) {
        this.posse = posse;
        this.limite = limite;
        this.agoraEmNanos = agoraEmNanos;
    }

    /**
     * @param analistas           usuários Analista com vínculo (repetidos pela chave contam uma vez)
     * @param limiteDeRequerimentos teto de requerimentos candidatos da leitura inteira
     * @param tempoTotal          tempo para todas as consultas, incluindo a espera por vaga
     */
    public LeituraDaPosse ler(Collection<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao,
            int limiteDeRequerimentos, Duration tempoTotal) {
        Map<ChaveDoResponsavel, AnalistaParaLeitura> distintos = new LinkedHashMap<>();
        analistas.forEach(a -> distintos.putIfAbsent(a.chave(), a));
        if (distintos.isEmpty() || definicao.vazia() || limiteDeRequerimentos < 1) {
            return LeituraDaPosse.NENHUMA;
        }
        long prazo = agoraEmNanos.getAsLong() + tempoTotal.toNanos();
        List<AnalistaParaLeitura> todos = List.copyOf(distintos.values());
        List<LeituraDaPosse> lidas = new ArrayList<>();
        int restante = limiteDeRequerimentos;
        boolean passouDoTeto = false;
        for (int i = 0; i < todos.size() && restante > 0; i += PosseDoAnalistaEmLote.MAXIMO_POR_LOTE) {
            List<AnalistaParaLeitura> grupo = todos.subList(i, Math.min(todos.size(), i + PosseDoAnalistaEmLote.MAXIMO_POR_LOTE));
            int pedido = restante + 1;
            Duration tempo = tempoQueResta(prazo);
            List<Long> candidatos = List.copyOf(new java.util.LinkedHashSet<>(
                    limite.executar(tempo, () -> posse.candidatos(grupo, definicao, pedido, tempo))));
            if (candidatos.size() > restante) {
                passouDoTeto = true;
                candidatos = candidatos.subList(0, restante);
            }
            restante -= candidatos.size();
            for (int j = 0; j < candidatos.size(); j += PosseDoAnalistaEmLote.MAXIMO_POR_LOTE) {
                List<Long> lote = candidatos.subList(j,
                        Math.min(candidatos.size(), j + PosseDoAnalistaEmLote.MAXIMO_POR_LOTE));
                Duration tempoDoLote = tempoQueResta(prazo);
                lidas.add(limite.executar(tempoDoLote, () -> posse.ler(lote, grupo, definicao, tempoDoLote)));
            }
        }
        LeituraDaPosse junta = LeituraDaPosse.juntar(lidas);
        return passouDoTeto ? new LeituraDaPosse(junta.requerimentos(), true) : junta;
    }

    private Duration tempoQueResta(long prazo) {
        Duration restante = Duration.ofNanos(prazo - agoraEmNanos.getAsLong());
        if (restante.isNegative() || restante.isZero()) {
            throw new TempoDaCaixaEsgotado();
        }
        return restante;
    }

    /** O tempo total da leitura acabou antes da última consulta. */
    public static final class TempoDaCaixaEsgotado extends RuntimeException {
        TempoDaCaixaEsgotado() {
            super("tempo da leitura da caixa esgotado", null, false, false);
        }
    }
}
