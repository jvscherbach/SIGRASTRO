package br.gov.mt.sema.rastro.painel.domain;

/** Um mês do gráfico 1 (1 a 12): protocolados e concluídos (mês aproximado, NÃO CONFIRMADO). */
public record MesDaSerie(int mes, long protocolados, long concluidos) {

    public MesDaSerie {
        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException("mês fora de 1 a 12");
        }
    }
}
