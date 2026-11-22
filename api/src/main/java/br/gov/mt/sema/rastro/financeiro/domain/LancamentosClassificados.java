package br.gov.mt.sema.rastro.financeiro.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado da regra de validade de um requerimento (S9, bloco 2): os lançamentos que ficam ({@code mantidos}, em ordem de
 * emissão), os ignorados com o motivo e os avisos. Os ignorados só servem ao diagnóstico do Administrador.
 */
public record LancamentosClassificados(List<LancamentoMantido> mantidos, List<LancamentoIgnorado> ignorados,
        boolean alemDoLimite) {

    public static final LancamentosClassificados NENHUM = new LancamentosClassificados(List.of(), List.of(), false);

    public LancamentosClassificados {
        mantidos = List.copyOf(mantidos);
        ignorados = List.copyOf(ignorados);
    }

    public boolean semLancamento() {
        return mantidos.isEmpty();
    }

    /** Contagem dos ignorados por motivo; todo motivo aparece, com zero quando não houve. */
    public Map<MotivoDoIgnorado, Integer> ignoradosPorMotivo() {
        Map<MotivoDoIgnorado, Integer> contagem = new EnumMap<>(MotivoDoIgnorado.class);
        for (MotivoDoIgnorado motivo : MotivoDoIgnorado.values()) {
            contagem.put(motivo, 0);
        }
        ignorados.forEach(i -> contagem.merge(i.motivo(), 1, Integer::sum));
        return Collections.unmodifiableMap(contagem);
    }

    /** Avisos ao usuário, sem id, DAR nem valor: inconsistência (um, com a contagem) e leitura além do limite. */
    public List<AvisoFinanceiro> avisos() {
        List<AvisoFinanceiro> avisos = new ArrayList<>();
        long inconsistentes = mantidos.stream().filter(m -> !m.consistente()).count();
        if (inconsistentes > 0) {
            avisos.add(new AvisoFinanceiro(CodigoDoAvisoFinanceiro.LANCAMENTO_INCONSISTENTE, inconsistentes == 1
                    ? "Um lançamento de taxa está com dados incompletos ou fora do padrão no SIGA; ele é mostrado como está."
                    : inconsistentes + " lançamentos de taxa estão com dados incompletos ou fora do padrão no SIGA; "
                            + "eles são mostrados como estão.",
                    AvisoFinanceiro.Gravidade.ATENCAO));
        }
        if (alemDoLimite) {
            avisos.add(new AvisoFinanceiro(CodigoDoAvisoFinanceiro.LANCAMENTOS_ALEM_DO_LIMITE,
                    "O requerimento tem mais lançamentos de taxa do que o limite de leitura; os mais recentes não são "
                            + "mostrados.",
                    AvisoFinanceiro.Gravidade.ATENCAO));
        }
        return List.copyOf(avisos);
    }
}
