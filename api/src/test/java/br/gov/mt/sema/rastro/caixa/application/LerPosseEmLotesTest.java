package br.gov.mt.sema.rastro.caixa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import br.gov.mt.sema.rastro.caixa.application.port.PosseDoAnalistaEmLote;
import br.gov.mt.sema.rastro.caixa.domain.AnalistaParaLeitura;
import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.LeituraDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.PosseLida;
import br.gov.mt.sema.rastro.tramitacao.application.LimiteDeLeiturasDoCamunda;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import org.junit.jupiter.api.Test;

/** Lotes de até 500, cada consulta pelo limite de leituras do Camunda, teto e tempo total (S11, bloco 4). Sem Docker. */
class LerPosseEmLotesTest {

    private static final DefinicaoDaPosse DEFINICAO = new DefinicaoDaPosse(Set.of("modeloFicticio"),
            Set.of("taskAnalisar", "taskRevisar"), Set.of("taskCorrecao"));
    private static final byte[] SAL = "sal-de-teste-0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private static AnalistaParaLeitura analista(int n) {
        IdentificadorDoResponsavel id = IdentificadorDoResponsavel.de(String.format("0000000%04d", n)).orElseThrow();
        return new AnalistaParaLeitura(ChaveDoResponsavel.de(id, SAL), id);
    }

    private static List<AnalistaParaLeitura> analistas(int quantos) {
        return IntStream.rangeClosed(1, quantos).mapToObj(LerPosseEmLotesTest::analista).toList();
    }

    /** Dublê da porta: devolve {@code candidatos} em ordem e uma posse por id pedido; guarda tamanhos e tempos. */
    private static final class Porta implements PosseDoAnalistaEmLote {
        final long candidatosPorLote;
        final List<Integer> analistasPorConsulta = new ArrayList<>();
        final List<Integer> maximos = new ArrayList<>();
        final List<Integer> detalhes = new ArrayList<>();
        final List<Duration> tempos = new ArrayList<>();
        Runnable durante = () -> { };
        boolean linhasDemais;

        Porta(long candidatosPorLote) {
            this.candidatosPorLote = candidatosPorLote;
        }

        @Override
        public List<Long> candidatos(List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao, int maximo,
                Duration tempoLimite) {
            assertThat(analistas).hasSizeLessThanOrEqualTo(MAXIMO_POR_LOTE);
            analistasPorConsulta.add(analistas.size());
            maximos.add(maximo);
            tempos.add(tempoLimite);
            durante.run();
            long base = analistasPorConsulta.size() * 100_000L;
            return LongStream.rangeClosed(1, Math.min(candidatosPorLote, maximo)).map(i -> base + i).boxed().toList();
        }

        @Override
        public LeituraDaPosse ler(List<Long> ids, List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao,
                Duration tempoLimite) {
            assertThat(ids).hasSizeLessThanOrEqualTo(MAXIMO_POR_LOTE);
            assertThat(analistas).hasSizeLessThanOrEqualTo(MAXIMO_POR_LOTE);
            detalhes.add(ids.size());
            tempos.add(tempoLimite);
            durante.run();
            return new LeituraDaPosse(ids.stream().map(id -> new PosseLida(id, Optional.of(id + "/2028"),
                    "modeloFicticio", List.of(new PosseLida.TarefaAtivaDaPosse("taskAnalisar", null,
                            Optional.of(analistas.getFirst().chave()))), Optional.empty(), Optional.of("Interessado Exemplo"),
                    Optional.empty())).toList(), linhasDemais);
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

    @Test
    void mil_e_duzentos_candidatos_viram_tres_lotes_de_detalhe() {
        Porta porta = new Porta(1_200);
        LeituraDaPosse lida = new LerPosseEmLotes(porta, limite(4)).ler(analistas(3), DEFINICAO, 20_000,
                Duration.ofSeconds(60));
        assertThat(porta.analistasPorConsulta).containsExactly(3);
        assertThat(porta.detalhes).containsExactly(500, 500, 200);
        assertThat(lida.requerimentos()).hasSize(1_200);
        assertThat(lida.incompleta()).isFalse();
    }

    @Test
    void mais_de_quinhentos_analistas_viram_lotes_de_analistas() {
        Porta porta = new Porta(10);
        LeituraDaPosse lida = new LerPosseEmLotes(porta, limite(4)).ler(analistas(1_100), DEFINICAO, 20_000,
                Duration.ofSeconds(60));
        assertThat(porta.analistasPorConsulta).containsExactly(500, 500, 100);
        assertThat(porta.detalhes).containsExactly(10, 10, 10);
        assertThat(lida.requerimentos()).hasSize(30);
    }

    @Test
    void analistas_repetidos_contam_uma_vez_e_sem_analista_ou_definicao_nao_consulta() {
        Porta porta = new Porta(5);
        LerPosseEmLotes leitor = new LerPosseEmLotes(porta, limite(4));
        List<AnalistaParaLeitura> repetidos = new ArrayList<>(analistas(2));
        repetidos.addAll(analistas(2));
        leitor.ler(repetidos, DEFINICAO, 100, Duration.ofSeconds(60));
        assertThat(porta.analistasPorConsulta).containsExactly(2);
        assertThat(leitor.ler(List.of(), DEFINICAO, 100, Duration.ofSeconds(60))).isEqualTo(LeituraDaPosse.NENHUMA);
        assertThat(leitor.ler(analistas(2), new DefinicaoDaPosse(Set.of(), Set.of(), Set.of()), 100,
                Duration.ofSeconds(60))).isEqualTo(LeituraDaPosse.NENHUMA);
        assertThat(porta.analistasPorConsulta).hasSize(1);
    }

    @Test
    void passou_do_teto_de_requerimentos_volta_incompleta_e_cortada_no_teto() {
        Porta porta = new Porta(1_000);
        LeituraDaPosse lida = new LerPosseEmLotes(porta, limite(4)).ler(analistas(3), DEFINICAO, 700,
                Duration.ofSeconds(60));
        assertThat(porta.maximos).as("pede o teto + 1").containsExactly(701);
        assertThat(lida.requerimentos()).hasSize(700);
        assertThat(lida.incompleta()).isTrue();
        assertThat(porta.detalhes).containsExactly(500, 200);
    }

    @Test
    void linhas_demais_em_um_lote_deixam_a_leitura_incompleta() {
        Porta porta = new Porta(3);
        porta.linhasDemais = true;
        assertThat(new LerPosseEmLotes(porta, limite(4)).ler(analistas(1), DEFINICAO, 100, Duration.ofSeconds(60))
                .incompleta()).isTrue();
    }

    @Test
    void cada_consulta_ocupa_uma_vaga_do_limite_de_leituras() {
        LimiteDeLeiturasDoCamunda limite = limite(1);
        Porta porta = new Porta(600);
        List<Boolean> semVaga = new ArrayList<>();
        porta.durante = () -> {
            try {
                limite.executar(Duration.ZERO, () -> "outra leitura");
                semVaga.add(false);
            } catch (LimiteDeLeiturasDoCamunda.SemVagaNoCamunda esperado) {
                semVaga.add(true);
            }
        };
        new LerPosseEmLotes(porta, limite).ler(analistas(2), DEFINICAO, 20_000, Duration.ofSeconds(60));
        // 1 consulta de candidatos + 2 de detalhe (500 + 100)
        assertThat(semVaga).containsExactly(true, true, true);
        assertThat(limite.executar(Duration.ZERO, () -> "livre de novo")).isEqualTo("livre de novo");
    }

    @Test
    void sem_vaga_dentro_do_tempo_falha_sem_consultar() {
        LimiteDeLeiturasDoCamunda limite = limite(1);
        Porta porta = new Porta(5);
        limite.executar(Duration.ZERO, () -> {
            assertThatThrownBy(() -> new LerPosseEmLotes(porta, limite).ler(analistas(2), DEFINICAO, 100,
                    Duration.ofMillis(50))).isInstanceOf(LimiteDeLeiturasDoCamunda.SemVagaNoCamunda.class);
            return null;
        });
        assertThat(porta.analistasPorConsulta).isEmpty();
    }

    @Test
    void cada_consulta_recebe_so_o_tempo_que_resta_e_acabou_o_tempo_para() {
        AtomicLong agora = new AtomicLong();
        Porta porta = new Porta(1_500);
        porta.durante = () -> agora.addAndGet(Duration.ofSeconds(25).toNanos());
        LerPosseEmLotes leitor = new LerPosseEmLotes(porta, limite(4), agora::get);
        assertThatThrownBy(() -> leitor.ler(analistas(2), DEFINICAO, 20_000, Duration.ofSeconds(60)))
                .isInstanceOf(LerPosseEmLotes.TempoDaCaixaEsgotado.class);
        assertThat(porta.tempos).containsExactly(Duration.ofSeconds(60), Duration.ofSeconds(35), Duration.ofSeconds(10));
        assertThat(porta.detalhes).containsExactly(500, 500);
    }

    @Test
    void falha_da_porta_sobe_sem_resultado_parcial() {
        PosseDoAnalistaEmLote quebrada = new PosseDoAnalistaEmLote() {
            @Override
            public List<Long> candidatos(List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao, int maximo,
                    Duration tempoLimite) {
                throw new IllegalStateException("banco fora do ar");
            }

            @Override
            public LeituraDaPosse ler(List<Long> ids, List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao,
                    Duration tempoLimite) {
                throw new IllegalStateException("não deveria ser chamada");
            }
        };
        assertThatThrownBy(() -> new LerPosseEmLotes(quebrada, limite(4)).ler(analistas(1), DEFINICAO, 100,
                Duration.ofSeconds(10))).isInstanceOf(IllegalStateException.class).hasMessage("banco fora do ar");
    }
}
