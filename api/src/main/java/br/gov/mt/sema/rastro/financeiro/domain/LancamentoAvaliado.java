package br.gov.mt.sema.rastro.financeiro.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Um lançamento mantido, avaliado (S9, bloco 3): ordem de emissão, número da taxa ("1ª taxa", "2ª taxa"), situação como o
 * SIGA a grava e a marca de taxa vencida.
 *
 * @param ordem          posição na lista (1, 2, ...), por emissão
 * @param numeroDaTaxa   número entre as <b>guias</b> (todo lançamento menos isenção e status desconhecido); {@code null}
 *                       nos que não são guia. A etiqueta "da correção" é do bloco 5 (precisa da tramitação)
 * @param vencida        conta como vencida no resumo: {@link #vencidaPorData} ou {@code STATUS = VENCIDA} ativo e sem
 *                       pagamento
 * @param vencidaPorData {@code AGUARDANDO_PAGAMENTO} com vencimento <b>anterior</b> a hoje em Cuiabá: o SIGA ainda não a
 *                       marcou. Só este caso ganha o aviso {@code TAXA_VENCIDA}; quando o SIGA marcar {@code VENCIDA}, o aviso
 *                       some sozinho e a situação passa a dizer
 * @param daCorrecao     guia emitida <b>depois</b> da entrada no ramo de correção da tramitação (bloco 5); falso sem
 *                       caminho exato (Camunda fora, plano B): aí vale "1ª taxa", "2ª taxa"
 */
public record LancamentoAvaliado(LancamentoMantido mantido, int ordem, Integer numeroDaTaxa,
        SituacaoFinanceira situacao, boolean vencida, boolean vencidaPorData, boolean daCorrecao) {

    public static final String ROTULO_DA_CORRECAO = "Taxa da correção";

    public Lancamento lancamento() {
        return mantido.lancamento();
    }

    /** "1ª taxa", "2ª taxa" ou "Taxa da correção"; vazio para isenção e status desconhecido. */
    public Optional<String> rotulo() {
        return Optional.ofNullable(numeroDaTaxa).map(n -> daCorrecao ? ROTULO_DA_CORRECAO : n + "ª taxa");
    }

    public Optional<LocalDate> emitidaEm() {
        return Optional.ofNullable(CalendarioDeCuiaba.data(lancamento().emitidoEm()));
    }

    public Optional<LocalDate> venceEm() {
        return Optional.ofNullable(CalendarioDeCuiaba.data(lancamento().venceEm()));
    }

    public Optional<LocalDate> pagaEm() {
        return Optional.ofNullable(CalendarioDeCuiaba.data(lancamento().pagoEm()));
    }

    /** Valor ausente é "não informado" (vazio), nunca zero. */
    public Optional<BigDecimal> valor() {
        return Optional.ofNullable(lancamento().valor());
    }

    public Optional<BigDecimal> desconto() {
        return Optional.ofNullable(lancamento().desconto());
    }

    public Optional<BigDecimal> upf() {
        return Optional.ofNullable(lancamento().upf());
    }

    public Optional<String> numeroDar() {
        return Optional.ofNullable(lancamento().numeroDar());
    }
}
