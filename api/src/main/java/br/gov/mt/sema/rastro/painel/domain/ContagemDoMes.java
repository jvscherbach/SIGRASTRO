package br.gov.mt.sema.rastro.painel.domain;

import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;

/**
 * Uma linha da série lida do SIGA: o mês (ano e mês) e quantos requerimentos caem nele. Mês vazio quando a coluna de data
 * está nula (ex.: concluído sem {@code DATA_ALTERACAO}); quem monta a série decide o que fazer com ele (bloco 3).
 */
public record ContagemDoMes(Optional<YearMonth> mes, long total) {

    public ContagemDoMes {
        Objects.requireNonNull(mes);
        if (total < 0) {
            throw new IllegalArgumentException("contagem negativa");
        }
    }

    public static ContagemDoMes de(YearMonth mes, long total) {
        return new ContagemDoMes(Optional.ofNullable(mes), total);
    }
}
