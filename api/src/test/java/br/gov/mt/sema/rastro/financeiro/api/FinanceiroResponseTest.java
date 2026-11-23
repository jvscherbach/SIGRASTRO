package br.gov.mt.sema.rastro.financeiro.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import br.gov.mt.sema.rastro.financeiro.api.dto.FinanceiroResponse;
import br.gov.mt.sema.rastro.financeiro.application.FinanceiroAberto;
import br.gov.mt.sema.rastro.financeiro.application.FinanceiroAberto.Estado;
import br.gov.mt.sema.rastro.financeiro.domain.AvaliarSituacao;
import br.gov.mt.sema.rastro.financeiro.domain.ClassificarValidade;
import br.gov.mt.sema.rastro.financeiro.domain.HistoricoFinanceiro;
import br.gov.mt.sema.rastro.financeiro.domain.Lancamento;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

/** O que sai e o que não sai do DTO do financeiro, sem subir o Spring (o desligamento do parâmetro de valores). */
class FinanceiroResponseTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-03-20T14:00:00Z"), ZoneId.of("America/Cuiaba"));
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 9, 0);

    private static HistoricoFinanceiro historico() {
        Lancamento paga = new Lancamento(1, 9001, "PAGA", false, "P", "FICT-0001", T0, T0.plusDays(10), T0.plusDays(3), null,
                new BigDecimal("980.00"), new BigDecimal("10.00"), new BigDecimal("28.0000"), null);
        Lancamento ignorado = new Lancamento(2, 9001, "CANCELADA", false, "P", null, T0, null, null, T0, null, null, null,
                null);
        return AvaliarSituacao.avaliar(ClassificarValidade.classificar(
                new LeituraDosLancamentos(List.of(paga, ignorado), false)), RELOGIO);
    }

    private static JsonNode serializar(boolean mostrarValores, boolean diagnostico) throws Exception {
        FinanceiroAberto aberto = new FinanceiroAberto(9001, "9001/2026", "9900/2026", Estado.ATIVO, historico(),
                mostrarValores, diagnostico);
        return JSON.readTree(JSON.writeValueAsString(FinanceiroResponse.de(aberto)));
    }

    @Test
    void valoresLigadosSaemEOsCamposDoAdministradorNaoExistemParaOsOutros() throws Exception {
        JsonNode r = serializar(true, false);
        JsonNode l = r.get("lancamentos").get(0);
        assertThat(l.get("valor").decimalValue()).isEqualByComparingTo("980.00");
        assertThat(l.get("desconto").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(l.get("upf").decimalValue()).isEqualByComparingTo("28.0000");
        assertThat(l.has("idTecnico")).isFalse();
        assertThat(l.has("statusProcessado")).isFalse();
        assertThat(l.has("passouPelaReaprovacaoDaCorrecao")).isFalse();
        assertThat(l.has("inconsistencias")).isFalse();
        assertThat(r.has("ignoradosPorMotivo")).isFalse();
    }

    @Test
    void valoresDesligadosSomemDoCorpoMasODarContinua() throws Exception {
        for (boolean administrador : new boolean[] {false, true}) {
            JsonNode r = serializar(false, administrador);
            JsonNode l = r.get("lancamentos").get(0);
            assertThat(r.get("mostraValores").asBoolean()).isFalse();
            assertThat(l.has("valor")).isFalse();
            assertThat(l.has("desconto")).isFalse();
            assertThat(l.has("upf")).isFalse();
            assertThat(l.get("numeroDar").asText()).isEqualTo("FICT-0001");
            assertThat(JSON.writeValueAsString(r)).doesNotContain("980").doesNotContain("28.0");
        }
    }

    @Test
    void administradorRecebeIdsEIgnoradosPorMotivo() throws Exception {
        JsonNode r = serializar(true, true);
        assertThat(r.get("lancamentos").get(0).get("idTecnico").asLong()).isEqualTo(1L);
        assertThat(r.get("ignoradosPorMotivo").get("FALSO_SEM_DAR").asInt()).isEqualTo(1);
    }

    @Test
    void indisponivelNaoTemHistoricoNemResumo() throws Exception {
        FinanceiroAberto aberto = new FinanceiroAberto(9001, "9001/2026", "9900/2026", Estado.INDISPONIVEL, null, true, true);
        JsonNode r = JSON.readTree(JSON.writeValueAsString(FinanceiroResponse.de(aberto)));
        assertThat(r.get("estado").asText()).isEqualTo("INDISPONIVEL");
        assertThat(r.get("resumo").isNull()).isTrue();
        assertThat(r.get("lancamentos")).isEmpty();
        assertThat(r.has("ignoradosPorMotivo")).isFalse();
    }
}
