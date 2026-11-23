package br.gov.mt.sema.rastro.painel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import br.gov.mt.sema.rastro.painel.application.port.TarefasAtivasEmLote;
import br.gov.mt.sema.rastro.painel.domain.LeituraDasTarefas;
import br.gov.mt.sema.rastro.painel.domain.ResponsavelDaTarefa;
import br.gov.mt.sema.rastro.painel.domain.TarefaAtiva;
import br.gov.mt.sema.rastro.tramitacao.application.LimiteDeLeiturasDoCamunda;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import org.junit.jupiter.api.Test;

/** Lotes de até 500, cada um pelo limite de leituras do Camunda, dentro do tempo total (S10, bloco 2). Sem Docker. */
class LerTarefasEmLotesTest {

    private static final List<String> MODELOS = List.of("modeloFicticio");

    /** Dublê da porta: guarda o tamanho e o tempo de cada lote e devolve uma tarefa por id. */
    private static final class Porta implements TarefasAtivasEmLote {
        final List<Integer> tamanhos = new ArrayList<>();
        final List<Duration> tempos = new ArrayList<>();
        Runnable durante = () -> { };

        @Override
        public LeituraDasTarefas ler(List<Long> ids, Collection<String> chaves, Duration tempoLimite) {
            assertThat(ids).hasSizeLessThanOrEqualTo(MAXIMO_POR_LOTE);
            assertThat(chaves).isEqualTo(MODELOS);
            tamanhos.add(ids.size());
            tempos.add(tempoLimite);
            durante.run();
            return new LeituraDasTarefas(ids.stream().map(id -> new TarefaAtiva(id, "modeloFicticio", "taskFicticia",
                    ResponsavelDaTarefa.NAO_ATRIBUIDA, java.util.Optional.empty(), List.of())).toList(),
                    Set.copyOf(ids), false);
        }
    }

    private static LimiteDeLeiturasDoCamunda limite(int vagas) {
        return new LimiteDeLeiturasDoCamunda(new ParametrosDoRastro() {
            @Override
            public boolean mostrarNomeDoResponsavel() {
                return true;
            }

            @Override
            public int maximoDeNomesPorEquipe() {
                return 10;
            }

            @Override
            public int cacheDaPosicaoSegundos() {
                return 0;
            }

            @Override
            public int posicoesSimultaneas() {
                return 1;
            }

            @Override
            public int camundaTimeoutSegundos() {
                return 10;
            }

            @Override
            public int leiturasSimultaneasDoCamunda() {
                return vagas;
            }
        });
    }

    private static List<Long> ids(int quantos) {
        return LongStream.rangeClosed(1, quantos).boxed().toList();
    }

    @Test
    void mil_e_duzentos_ids_viram_tres_lotes_e_o_resultado_e_juntado() {
        Porta porta = new Porta();
        LeituraDasTarefas lidas = new LerTarefasEmLotes(porta, limite(4)).ler(ids(1_200), MODELOS, Duration.ofSeconds(60));
        assertThat(porta.tamanhos).containsExactly(500, 500, 200);
        assertThat(lidas.tarefas()).hasSize(1_200);
        assertThat(lidas.comInstancia()).hasSize(1_200);
    }

    @Test
    void ids_repetidos_contam_uma_vez_e_vazio_nao_consulta() {
        Porta porta = new Porta();
        LerTarefasEmLotes leitor = new LerTarefasEmLotes(porta, limite(4));
        List<Long> repetidos = new ArrayList<>(ids(500));
        repetidos.addAll(ids(500));
        leitor.ler(repetidos, MODELOS, Duration.ofSeconds(60));
        assertThat(porta.tamanhos).containsExactly(500);
        assertThat(leitor.ler(List.of(), MODELOS, Duration.ofSeconds(60))).isEqualTo(LeituraDasTarefas.NENHUMA);
        assertThat(porta.tamanhos).hasSize(1);
    }

    @Test
    void cada_lote_ocupa_uma_vaga_do_limite_de_leituras() {
        LimiteDeLeiturasDoCamunda limite = limite(1);
        Porta porta = new Porta();
        List<Boolean> semVaga = new ArrayList<>();
        // Durante cada lote, a única vaga está ocupada: outra leitura do Camunda não consegue entrar.
        porta.durante = () -> {
            try {
                limite.executar(Duration.ZERO, () -> "outra leitura");
                semVaga.add(false);
            } catch (LimiteDeLeiturasDoCamunda.SemVagaNoCamunda esperado) {
                semVaga.add(true);
            }
        };
        new LerTarefasEmLotes(porta, limite).ler(ids(1_200), MODELOS, Duration.ofSeconds(60));
        assertThat(semVaga).containsExactly(true, true, true);
        assertThat(limite.executar(Duration.ZERO, () -> "livre de novo")).isEqualTo("livre de novo");
    }

    @Test
    void sem_vaga_dentro_do_tempo_falha_sem_consultar() {
        LimiteDeLeiturasDoCamunda limite = limite(1);
        Porta porta = new Porta();
        limite.executar(Duration.ZERO, () -> {
            assertThatThrownBy(() -> new LerTarefasEmLotes(porta, limite).ler(ids(10), MODELOS, Duration.ofMillis(50)))
                    .isInstanceOf(LimiteDeLeiturasDoCamunda.SemVagaNoCamunda.class);
            return null;
        });
        assertThat(porta.tamanhos).isEmpty();
    }

    @Test
    void cada_lote_recebe_so_o_tempo_que_resta_e_acabou_o_tempo_para() {
        AtomicLong agora = new AtomicLong();
        Porta porta = new Porta();
        porta.durante = () -> agora.addAndGet(Duration.ofSeconds(25).toNanos()); // cada lote leva 25 s
        LerTarefasEmLotes leitor = new LerTarefasEmLotes(porta, limite(4), agora::get);
        assertThatThrownBy(() -> leitor.ler(ids(2_000), MODELOS, Duration.ofSeconds(60)))
                .isInstanceOf(LerTarefasEmLotes.TempoDoPainelEsgotado.class);
        assertThat(porta.tamanhos).as("60 s: dois lotes de 25 s e o terceiro com 10 s; o quarto não sai")
                .containsExactly(500, 500, 500);
        assertThat(porta.tempos).containsExactly(Duration.ofSeconds(60), Duration.ofSeconds(35), Duration.ofSeconds(10));
    }
}
