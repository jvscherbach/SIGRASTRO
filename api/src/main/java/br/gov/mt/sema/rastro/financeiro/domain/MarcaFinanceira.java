package br.gov.mt.sema.rastro.financeiro.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Uma marca financeira numa passagem da tramitação (S9, bloco 5): "Emitida em", "DAR", "Vence em", "Paga em", "Taxa
 * vencida", "Isento de Taxa" e a etiqueta "da correção". Só fatos já lidos do {@code LANCAMENTO_TAXA}; nenhuma marca é
 * inventada para uma passagem que não tem lançamento casado.
 *
 * @param data           data pura (Cuiabá), quando a marca é de data
 * @param valor          o número do DAR, quando a marca é o DAR
 * @param dataAConfirmar só no "Isento de Taxa" cuja data não veio do lançamento (bloco 4)
 */
public record MarcaFinanceira(Tipo tipo, Optional<LocalDate> data, Optional<String> valor, boolean dataAConfirmar) {

    public enum Tipo {
        EMITIDA_EM("Emitida em"),
        NUMERO_DAR("DAR"),
        VENCE_EM("Vence em"),
        PAGA_EM("Paga em"),
        TAXA_VENCIDA("Taxa vencida"),
        ISENTO_DE_TAXA(RegistroDeIsencao.TEXTO),
        DA_CORRECAO("da correção");

        private final String rotulo;

        Tipo(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    static MarcaFinanceira data(Tipo tipo, LocalDate data) {
        return new MarcaFinanceira(tipo, Optional.of(data), Optional.empty(), false);
    }

    static MarcaFinanceira etiqueta(Tipo tipo) {
        return new MarcaFinanceira(tipo, Optional.empty(), Optional.empty(), false);
    }
}
