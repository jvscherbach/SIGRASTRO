package br.gov.mt.sema.rastro.financeiro.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Situação financeira, taxa vencida por data e ciclos (S9, bloco 3; docs/15). Avalia os lançamentos <b>mantidos</b> pela
 * regra de validade, um a um, e deriva o resumo; os ignorados nunca entram.
 *
 * <p><b>Taxa vencida</b> (o SIGA não atualiza o status quando o prazo passa): lançamento ativo ({@code DATA_FIM} nula) e
 * sem {@code DATA_PAGAMENTO}, e (a) {@code AGUARDANDO_PAGAMENTO} válido (sem inconsistência) com a <b>data</b> de
 * vencimento anterior a hoje em Cuiabá (vencer hoje ainda não é vencida; sem data de vencimento, não se afirma nada), ou
 * (b) {@code STATUS = VENCIDA} (a marca do próprio SIGA, aceita como está). Só (a) ganha o aviso
 * {@link CodigoDoAvisoFinanceiro#TAXA_VENCIDA}: a situação continua "aguardando pagamento", como no SIGA.
 *
 * <p><b>Peso de cada lançamento no resumo:</b> vencida, {@code VENCIDO}; {@code AGUARDANDO_EMISSAO} ou
 * {@code AGUARDANDO_PAGAMENTO} ativo, {@code EM_ABERTO}; {@code PAGA}, {@code QUITADO}; {@code ISENTA}, {@code ISENTO};
 * {@code CANCELADA}, aberto já encerrado e {@code VENCIDA} paga ou encerrada não pesam. Status desconhecido também não pesa;
 * se todos forem desconhecidos, o resumo é {@code INDETERMINADO}. Lançamento mantido com inconsistência e status conhecido
 * pesa pelo status (é mostrado como está), mas nunca é dado como vencido <b>por data</b>.
 *
 * <p><b>Ciclos:</b> as guias (todo lançamento menos isenção e status desconhecido) são numeradas por ordem de emissão,
 * "1ª taxa", "2ª taxa"; a etiqueta "da correção" é do bloco 5.
 */
public final class AvaliarSituacao {

    private AvaliarSituacao() {
    }

    public static HistoricoFinanceiro avaliar(LancamentosClassificados classificados, Clock relogio) {
        LocalDate hoje = CalendarioDeCuiaba.hoje(relogio);
        List<LancamentoAvaliado> avaliados = new ArrayList<>();
        List<AvisoFinanceiro> avisos = new ArrayList<>(classificados.avisos());
        ResumoFinanceiro resumo = ResumoFinanceiro.SEM_TAXA;
        boolean algumConhecido = false;
        int ordem = 0;
        int guias = 0;
        for (LancamentoMantido mantido : classificados.mantidos()) {
            Lancamento l = mantido.lancamento();
            SituacaoFinanceira situacao = SituacaoFinanceira.de(l);
            boolean guia = situacao != SituacaoFinanceira.ISENTA && situacao != SituacaoFinanceira.DESCONHECIDA;
            boolean vencidaPorData = vencidaPorData(mantido, situacao, hoje);
            boolean vencida = vencidaPorData || (situacao == SituacaoFinanceira.VENCIDA && emAberto(l));
            LancamentoAvaliado avaliado = new LancamentoAvaliado(mantido, ++ordem, guia ? ++guias : null, situacao,
                    vencida, vencidaPorData);
            avaliados.add(avaliado);
            if (situacao != SituacaoFinanceira.DESCONHECIDA) {
                algumConhecido = true;
                resumo = resumo.mais(peso(avaliado));
            }
            if (vencidaPorData) {
                avisos.add(new AvisoFinanceiro(CodigoDoAvisoFinanceiro.TAXA_VENCIDA,
                        "Taxa vencida em " + CalendarioDeCuiaba.formatar(avaliado.venceEm().orElseThrow()) + ".",
                        AvisoFinanceiro.Gravidade.ATENCAO));
            }
        }
        if (!avaliados.isEmpty() && !algumConhecido) {
            resumo = ResumoFinanceiro.INDETERMINADO;
        }
        return new HistoricoFinanceiro(avaliados, resumo, avisos, classificados);
    }

    /** Ativo e sem pagamento. */
    private static boolean emAberto(Lancamento l) {
        return l.ativo() && l.pagoEm() == null;
    }

    /**
     * A conclusão que é do Rastro (não do SIGA) só sai de dado limpo: lançamento sem inconsistência, ativo, sem pagamento,
     * com data de vencimento. A marca {@code VENCIDA} do próprio SIGA vale como está (basta estar ativo e sem pagamento).
     */
    static boolean vencidaPorData(LancamentoMantido mantido, SituacaoFinanceira situacao, LocalDate hoje) {
        Lancamento l = mantido.lancamento();
        LocalDate vencimento = CalendarioDeCuiaba.data(l.venceEm());
        return situacao == SituacaoFinanceira.AGUARDANDO_PAGAMENTO && mantido.consistente() && emAberto(l)
                && vencimento != null && vencimento.isBefore(hoje);
    }

    private static ResumoFinanceiro peso(LancamentoAvaliado a) {
        if (a.vencida()) {
            return ResumoFinanceiro.VENCIDO;
        }
        boolean ativo = a.lancamento().ativo();
        return switch (a.situacao()) {
            case EMITINDO, AGUARDANDO_PAGAMENTO -> ativo ? ResumoFinanceiro.EM_ABERTO : ResumoFinanceiro.SEM_TAXA;
            case PAGA -> ResumoFinanceiro.QUITADO;
            case ISENTA -> ResumoFinanceiro.ISENTO;
            case VENCIDA, CANCELADA, DESCONHECIDA -> ResumoFinanceiro.SEM_TAXA;
        };
    }
}
