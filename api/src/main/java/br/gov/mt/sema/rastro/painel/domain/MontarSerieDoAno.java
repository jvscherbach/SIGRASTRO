package br.gov.mt.sema.rastro.painel.domain;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta o gráfico 1 (função pura): 12 meses sempre presentes (mês sem dado = 0). Concluídos são, por regra, um subconjunto
 * dos protocolados do ano; se os dados disserem outra coisa (concluído com mês fora do ano ou sem mês, ou total maior que o
 * de protocolados), o painel avisa com {@link AvisoDoPainel#INCONSISTENCIA_NA_SERIE} e mostra os números como vieram.
 */
public final class MontarSerieDoAno {

    private MontarSerieDoAno() {
    }

    public static SerieDoAno montar(int ano, LeituraDaSerie leitura) {
        long[] protocolados = new long[12];
        long[] concluidos = new long[12];
        long concluidosForaDoAno = 0;
        long totalProtocolados = 0;
        long totalConcluidos = 0;
        for (ContagemDoMes c : leitura.protocolados()) {
            if (c.mes().isPresent() && c.mes().get().getYear() == ano) {
                protocolados[c.mes().get().getMonthValue() - 1] += c.total();
                totalProtocolados += c.total();
            }
        }
        for (ContagemDoMes c : leitura.concluidos()) {
            totalConcluidos += c.total();
            YearMonth mes = c.mes().orElse(null);
            if (mes != null && mes.getYear() == ano) {
                concluidos[mes.getMonthValue() - 1] += c.total();
            } else {
                concluidosForaDoAno += c.total();
            }
        }
        List<MesDaSerie> meses = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            meses.add(new MesDaSerie(i + 1, protocolados[i], concluidos[i]));
        }
        List<AvisoDoPainel> avisos = new ArrayList<>();
        if (concluidosForaDoAno > 0 || totalConcluidos > totalProtocolados) {
            avisos.add(new AvisoDoPainel(AvisoDoPainel.INCONSISTENCIA_NA_SERIE,
                    "Os concluídos não cabem nos protocolados do ano: confira a leitura antes de usar estes números."));
        }
        return new SerieDoAno(ano, meses, totalProtocolados, totalConcluidos, leitura.semDataDeProtocolo(), avisos);
    }
}
