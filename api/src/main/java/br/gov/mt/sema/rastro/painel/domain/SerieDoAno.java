package br.gov.mt.sema.rastro.painel.domain;

import java.util.List;

/**
 * Gráfico 1: sempre 12 meses. Os totais somam o que o SIGA devolveu para o ano (nada é escondido); {@code semDataDeProtocolo}
 * só alimenta o aviso.
 */
public record SerieDoAno(int ano, List<MesDaSerie> meses, long totalProtocolados, long totalConcluidos,
        long semDataDeProtocolo, List<AvisoDoPainel> avisos) {

    public SerieDoAno {
        meses = List.copyOf(meses);
        avisos = List.copyOf(avisos);
    }
}
