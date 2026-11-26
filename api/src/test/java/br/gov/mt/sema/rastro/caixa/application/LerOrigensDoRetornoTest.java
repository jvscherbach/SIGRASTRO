package br.gov.mt.sema.rastro.caixa.application;

import static br.gov.mt.sema.rastro.caixa.CenarioDaCaixaEmMemoria.historia;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import br.gov.mt.sema.rastro.caixa.domain.OrigemDoRetorno;
import br.gov.mt.sema.rastro.tramitacao.domain.MotivosDaVolta;
import org.junit.jupiter.api.Test;

/** Origem do retorno só dos avisados, com teto, tempo e falha tolerada (S11, bloco 5). Sem Docker. */
class LerOrigensDoRetornoTest {

    @Test
    void le_cada_id_uma_vez_e_passou_do_teto_vira_mudou_de_etapa() {
        AtomicInteger lidas = new AtomicInteger();
        LerOrigensDoRetorno leitor = new LerOrigensDoRetorno(id -> {
            lidas.incrementAndGet();
            return historia(MotivosDaVolta.ANALISE, MotivosDaVolta.REVISAO, MotivosDaVolta.ANALISE);
        }, System::nanoTime);
        List<Long> ids = LongStream.rangeClosed(1, LerOrigensDoRetorno.TETO_DE_LEITURAS + 20).boxed().toList();
        var origens = leitor.ler(ids, Duration.ofSeconds(60));
        assertThat(lidas.get()).isEqualTo(LerOrigensDoRetorno.TETO_DE_LEITURAS);
        assertThat(origens).hasSize(ids.size());
        assertThat(origens.get(1L)).isEqualTo(OrigemDoRetorno.VOLTOU_DA_REVISAO);
        assertThat(origens.get((long) ids.size())).isEqualTo(OrigemDoRetorno.MUDOU_DE_ETAPA);
        assertThat(leitor.ler(List.of(7L, 7L), Duration.ofSeconds(60))).hasSize(1);
    }

    @Test
    void falha_na_leitura_e_tempo_esgotado_nunca_inventam_origem() {
        LerOrigensDoRetorno falha = new LerOrigensDoRetorno(id -> {
            throw new IllegalStateException("Camunda fora do ar com CPF 00000000001");
        }, System::nanoTime);
        assertThat(falha.ler(List.of(1L, 2L), Duration.ofSeconds(60)).values())
                .containsOnly(OrigemDoRetorno.MUDOU_DE_ETAPA);

        AtomicLong relogio = new AtomicLong();
        AtomicInteger lidas = new AtomicInteger();
        LerOrigensDoRetorno lento = new LerOrigensDoRetorno(id -> {
            lidas.incrementAndGet();
            relogio.addAndGet(Duration.ofSeconds(30).toNanos());
            return historia(MotivosDaVolta.ANALISE, MotivosDaVolta.ANALISE);
        }, relogio::get);
        var origens = lento.ler(List.of(1L, 2L, 3L), Duration.ofSeconds(40));
        assertThat(lidas.get()).as("passou o tempo: o resto não é lido").isEqualTo(2);
        assertThat(origens.get(3L)).isEqualTo(OrigemDoRetorno.MUDOU_DE_ETAPA);
    }
}
