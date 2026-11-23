package br.gov.mt.sema.rastro.painel.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * O ano considerado pelo painel (S10): só entram os requerimentos com {@code DATA_PROTOCOLO_PROCESSO} dentro dele. O recorte
 * é por <b>intervalo</b> ({@code >=} 1º de janeiro e {@code <} 1º de janeiro seguinte, relógio de parede de Cuiabá, como as
 * colunas do SIGA), nunca por função na coluna, para o índice valer.
 *
 * <p>"Ano corrente" é o ano da data de Cuiabá no instante do relógio injetado (o fuso do relógio e o da JVM são ignorados):
 * às 22h de 31/12 em Cuiabá já é 1º/1 em UTC, e o ano continua o de Cuiabá. Mesma regra de
 * {@code financeiro.domain.CalendarioDeCuiaba#hoje}; repetida aqui porque o painel não depende do financeiro.
 */
public record AnoDoPainel(int ano) {

    public static final ZoneId FUSO = ZoneId.of("America/Cuiaba");
    /** Faixa aceita para um ano fixo ({@code rastro.painelAno}); {@code 0} quer dizer "ano corrente". */
    public static final int MINIMO = 2000;
    public static final int MAXIMO = 2100;
    public static final int ANO_CORRENTE = 0;

    public AnoDoPainel {
        if (ano < MINIMO || ano > MAXIMO) {
            throw new IllegalArgumentException("ano do painel fora de " + MINIMO + " a " + MAXIMO);
        }
    }

    /** O ano de hoje em Cuiabá. */
    public static AnoDoPainel corrente(Clock relogio) {
        return new AnoDoPainel(LocalDate.ofInstant(relogio.instant(), FUSO).getYear());
    }

    /** O valor do parâmetro: {@value #ANO_CORRENTE} é o ano corrente; qualquer outro é o próprio ano. */
    public static AnoDoPainel doParametro(int parametro, Clock relogio) {
        return parametro == ANO_CORRENTE ? corrente(relogio) : new AnoDoPainel(parametro);
    }

    /** 1º de janeiro, 00:00 (inclusivo). */
    public LocalDateTime inicio() {
        return LocalDate.of(ano, 1, 1).atStartOfDay();
    }

    /** 1º de janeiro do ano seguinte, 00:00 (exclusivo). */
    public LocalDateTime fimExclusivo() {
        return LocalDate.of(ano + 1, 1, 1).atStartOfDay();
    }
}
