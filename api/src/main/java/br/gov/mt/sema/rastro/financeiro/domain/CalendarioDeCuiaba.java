package br.gov.mt.sema.rastro.financeiro.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Datas do financeiro (S9, bloco 3), num só lugar. "Hoje" é sempre a data de Cuiabá, qualquer que seja o fuso do relógio
 * injetado ou da JVM. As datas do SIGA já chegam como relógio de parede de Cuiabá ({@code integration.siga.DataHoraDoSiga},
 * sem {@code Timestamp}), então a data de uma coluna {@code DATE}/{@code TIMESTAMP} é só a parte da data, <b>sem converter
 * fuso</b>: um vencimento gravado às 23:30 continua no mesmo dia.
 */
public final class CalendarioDeCuiaba {

    public static final ZoneId FUSO = ZoneId.of("America/Cuiaba");
    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CalendarioDeCuiaba() {
    }

    /** A data de hoje em Cuiabá no instante do relógio (o fuso do próprio relógio é ignorado). */
    public static LocalDate hoje(Clock relogio) {
        return LocalDate.ofInstant(relogio.instant(), FUSO);
    }

    /** A data (de Cuiabá) de uma coluna do SIGA, sem deslocar o dia; {@code null} continua {@code null}. */
    public static LocalDate data(LocalDateTime doSiga) {
        return doSiga == null ? null : doSiga.toLocalDate();
    }

    /** {@code dd/mm/aaaa}, para os textos de aviso. */
    public static String formatar(LocalDate data) {
        return data.format(DIA_MES_ANO);
    }
}
