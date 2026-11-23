package br.gov.mt.sema.rastro.painel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** O ano do painel (S10, bloco 2): intervalo fechado-aberto e ano corrente em Cuiabá, qualquer que seja o fuso do relógio. */
class AnoDoPainelTest {

    @Test
    void intervalo_de_primeiro_de_janeiro_ao_primeiro_de_janeiro_seguinte() {
        AnoDoPainel ano = new AnoDoPainel(2026);
        assertThat(ano.inicio()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(ano.fimExclusivo()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
    }

    @Test
    void ano_corrente_e_o_de_cuiaba_na_virada() {
        // 02:00 UTC de 1º/1 = 22:00 de 31/12 em Cuiabá (UTC-4)
        Clock emUtc = Clock.fixed(Instant.parse("2027-01-01T02:00:00Z"), ZoneOffset.UTC);
        assertThat(AnoDoPainel.corrente(emUtc).ano()).isEqualTo(2026);
        // 04:00 UTC de 1º/1 = 00:00 de 1º/1 em Cuiabá
        Clock meiaNoite = Clock.fixed(Instant.parse("2027-01-01T04:00:00Z"), ZoneOffset.UTC);
        assertThat(AnoDoPainel.corrente(meiaNoite).ano()).isEqualTo(2027);
        Clock emCuiaba = Clock.fixed(Instant.parse("2026-01-01T02:00:00Z"), AnoDoPainel.FUSO);
        assertThat(AnoDoPainel.corrente(emCuiaba).ano()).isEqualTo(2025);
    }

    @Test
    void parametro_zero_e_o_ano_corrente_e_outro_valor_e_o_proprio_ano() {
        Clock relogio = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);
        assertThat(AnoDoPainel.doParametro(0, relogio)).isEqualTo(new AnoDoPainel(2026));
        assertThat(AnoDoPainel.doParametro(2024, relogio)).isEqualTo(new AnoDoPainel(2024));
    }

    @Test
    void fora_da_faixa_e_recusado() {
        assertThatThrownBy(() -> new AnoDoPainel(1999)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnoDoPainel(2101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new AnoDoPainel(2000).ano()).isEqualTo(2000);
        assertThat(new AnoDoPainel(2100).ano()).isEqualTo(2100);
    }

    @Test
    void tarefa_sem_servidor_nao_guarda_nome() {
        TarefaAtiva t = new TarefaAtiva(1, "m", "t", ResponsavelDaTarefa.INTERESSADO,
                java.util.Optional.of("Nome que não deve ficar"), java.util.List.of());
        assertThat(t.nome()).isEmpty();
    }
}
