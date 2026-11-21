package br.gov.mt.sema.rastro.tramitacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Tipos crus do caminho percorrido (S8, bloco 2): sem banco. */
class CaminhoBrutoTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 3, 2, 8, 0);

    private static InstanciaDoHistorico instancia(String id, OrigemDaInstancia origem, boolean truncada) {
        return new InstanciaDoHistorico(id, origem, "chave", T, null, null, null, null, truncada, 0);
    }

    @Test
    void sem_instancia_e_vazio_e_sem_raiz() {
        CaminhoBruto c = CaminhoBruto.semInstancia();
        assertThat(c.vazio()).isTrue();
        assertThat(c.raiz()).isEmpty();
        assertThat(c.truncado()).isFalse();
        assertThat(c.instanciasRaiz()).isZero();
    }

    @Test
    void raiz_e_a_instancia_de_origem_raiz_e_truncado_vale_se_alguma_instancia_truncou() {
        CaminhoBruto c = new CaminhoBruto(List.of(instancia("r", OrigemDaInstancia.RAIZ, false),
                instancia("f", OrigemDaInstancia.FILHA, true)), 1, List.of(), List.of(), List.of());
        assertThat(c.raiz()).hasValueSatisfying(r -> assertThat(r.id()).isEqualTo("r"));
        assertThat(c.truncado()).isTrue();
    }

    @Test
    void variavel_fora_da_lista_branca_e_recusada() {
        assertThatThrownBy(() -> new VariavelBruta("i", "nomeDoRequerente", "string", "x", null, T))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valor_com_cara_de_cpf_ou_muito_longo_e_descartado() {
        assertThat(new VariavelBruta("i", "statusConferencia", "string", "00000000001", null, T).valor()).isEmpty();
        assertThat(new VariavelBruta("i", "statusConferencia", "string", "x".repeat(101), null, T).valor()).isEmpty();
        assertThat(new VariavelBruta("i", "isRevisaoAnalise", "long", null, 12345678901L, T).valor()).isEmpty();
        assertThat(new VariavelBruta("i", "statusConferencia", "string", "taxavel", null, T).valor())
                .isEqualTo(Optional.of("taxavel"));
        assertThat(new VariavelBruta("i", "isRevisaoAnalise", "boolean", null, 1L, T).valor())
                .isEqualTo(Optional.of("1"));
    }

    @Test
    void atividade_sem_fim_esta_aberta_e_o_cpf_nao_aparece_no_texto() {
        AtividadeBruta a = new AtividadeBruta("i", OrigemDaInstancia.RAIZ, "i-a000001", null, "e", "taskX", null,
                "userTask", T, null, IdentificadorDoResponsavel.de("00000000001"), null, Optional.empty());
        assertThat(a.aberta()).isTrue();
        assertThat(a.toString()).doesNotContain("00000000001");
    }
}
