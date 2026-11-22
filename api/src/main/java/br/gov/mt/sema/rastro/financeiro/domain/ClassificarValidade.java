package br.gov.mt.sema.rastro.financeiro.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Regra de validade do lançamento de taxa (S9, bloco 2; decisão do responsável, docs/09), aplicada a cada lançamento, um
 * a um:
 *
 * <ul>
 *   <li>{@code AGUARDANDO_EMISSAO} com {@code TAXA_ISENTA = 1}: <b>ignorado</b> ({@link MotivoDoIgnorado#RUIDO_ISENTO_AGUARDANDO},
 *       ruído, docs/10); sem a marca de isenta, <b>válido</b> (com ou sem DAR);</li>
 *   <li>{@code ISENTA}: <b>válido</b>, com ou sem DAR (isenção não gera DAR);</li>
 *   <li>{@code CANCELADA} sem DAR: <b>ignorado</b> ({@link MotivoDoIgnorado#FALSO_SEM_DAR}, criado e descartado pelo sistema);
 *       com DAR, <b>válido</b> (cancelada de verdade);</li>
 *   <li>qualquer outro status do enum com DAR: <b>válido</b>.</li>
 * </ul>
 *
 * <p>Fora disso, nada some em silêncio: status desconhecido, {@code AGUARDANDO_PAGAMENTO}/{@code PAGA}/{@code VENCIDA} sem
 * DAR e {@code PAGA} sem {@code DATA_PAGAMENTO} são <b>mantidos</b> com a {@link Inconsistencia} (aviso
 * {@link CodigoDoAvisoFinanceiro#LANCAMENTO_INCONSISTENTE}). A regra não olha {@code DATA_FIM} (um encerrado válido continua
 * na lista; o efeito dele no resumo é do bloco 3) nem {@code PASSOU_POR_REAPROV_TAXA_CORREC} (semântica não confirmada).
 *
 * <p>Ordem dos mantidos e dos ignorados: emissão (sem data no fim) e, no empate, id, a mesma do SQL, refeita aqui para não
 * depender do banco.
 */
public final class ClassificarValidade {

    static final Comparator<Lancamento> ORDEM_DE_EMISSAO = Comparator
            .comparing(Lancamento::emitidoEm, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(Lancamento::id);

    private ClassificarValidade() {
    }

    public static LancamentosClassificados classificar(LeituraDosLancamentos leitura) {
        List<LancamentoMantido> mantidos = new ArrayList<>();
        List<LancamentoIgnorado> ignorados = new ArrayList<>();
        leitura.lancamentos().stream().sorted(ORDEM_DE_EMISSAO).forEach(l -> motivoParaIgnorar(l).ifPresentOrElse(
                motivo -> ignorados.add(new LancamentoIgnorado(l, motivo)),
                () -> mantidos.add(new LancamentoMantido(l, inconsistencias(l)))));
        return new LancamentosClassificados(mantidos, ignorados, leitura.alemDoLimite());
    }

    static Optional<MotivoDoIgnorado> motivoParaIgnorar(Lancamento l) {
        if (l.eh(StatusDaGuia.CANCELADA) && !l.temDar()) {
            return Optional.of(MotivoDoIgnorado.FALSO_SEM_DAR);
        }
        if (l.eh(StatusDaGuia.AGUARDANDO_EMISSAO) && l.taxaIsenta()) {
            return Optional.of(MotivoDoIgnorado.RUIDO_ISENTO_AGUARDANDO);
        }
        return Optional.empty();
    }

    static Set<Inconsistencia> inconsistencias(Lancamento l) {
        Optional<StatusDaGuia> status = l.status();
        if (status.isEmpty()) {
            return EnumSet.of(Inconsistencia.STATUS_DESCONHECIDO);
        }
        Set<Inconsistencia> encontradas = EnumSet.noneOf(Inconsistencia.class);
        StatusDaGuia s = status.get();
        boolean exigeDar = s != StatusDaGuia.AGUARDANDO_EMISSAO && s != StatusDaGuia.ISENTA;
        if (exigeDar && !l.temDar()) {
            encontradas.add(Inconsistencia.SEM_DAR);
        }
        if (s == StatusDaGuia.PAGA && l.pagoEm() == null) {
            encontradas.add(Inconsistencia.PAGA_SEM_DATA_DE_PAGAMENTO);
        }
        return encontradas;
    }
}
