package br.gov.mt.sema.rastro.financeiro.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Um lançamento de taxa ({@code SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA}) como o SIGA o grava, imutável e sem interpretação
 * (S9, bloco 2). Só as colunas da lista fechada do prompt (docs/09); a chave do arquivo da guia, a mensagem e a titulação
 * nunca são lidas, então não existem aqui.
 *
 * <p><b>Ausente é ausente:</b> data, valor ou DAR que o SIGA não gravou ficam {@code null} (acessados por
 * {@code Optional} nos métodos de apoio). Valor ausente é "não informado", nunca zero (bloco 3).
 *
 * @param id                               {@code ID}: técnico, só para ordenar e para o diagnóstico do Administrador
 * @param statusBruto                      {@code STATUS} como gravado (pode estar fora do enum: {@link #status()})
 * @param taxaIsenta                       {@code TAXA_ISENTA = 1}
 * @param statusProcessado                 {@code STATUS_PROCESSADO} ({@code N}/{@code P}); só diagnóstico
 * @param numeroDar                        {@code NUM_DAR}; em branco vira {@code null}
 * @param emitidoEm                        {@code DATA_EMISSAO}
 * @param venceEm                          {@code DATA_VENCIMENTO} (a conversão para data pura é do bloco 3)
 * @param pagoEm                           {@code DATA_PAGAMENTO}
 * @param encerradoEm                      {@code DATA_FIM}; nula = lançamento ativo
 * @param valor                            {@code VALOR_REAL}
 * @param desconto                         {@code VALOR_DESCONTO}
 * @param upf                              {@code QTDE_TOTAL_UPF}
 * @param passouPelaReaprovacaoDaCorrecao {@code PASSOU_POR_REAPROV_TAXA_CORREC}: semântica não confirmada, só diagnóstico
 *                                         do Administrador, nunca regra (docs/10)
 */
public record Lancamento(long id, long idRequerimento, String statusBruto, boolean taxaIsenta, String statusProcessado,
        String numeroDar, LocalDateTime emitidoEm, LocalDateTime venceEm, LocalDateTime pagoEm,
        LocalDateTime encerradoEm, BigDecimal valor, BigDecimal desconto, BigDecimal upf,
        Boolean passouPelaReaprovacaoDaCorrecao) {

    public Lancamento {
        numeroDar = numeroDar == null || numeroDar.isBlank() ? null : numeroDar.strip();
        statusProcessado = statusProcessado == null || statusProcessado.isBlank() ? null : statusProcessado.strip();
    }

    /** O status no enum; vazio quando o SIGA gravou algo fora dele (ou nada). */
    public Optional<StatusDaGuia> status() {
        return StatusDaGuia.de(statusBruto);
    }

    public boolean temDar() {
        return numeroDar != null;
    }

    /** {@code DATA_FIM} nula. */
    public boolean ativo() {
        return encerradoEm == null;
    }

    public boolean eh(StatusDaGuia esperado) {
        return status().filter(esperado::equals).isPresent();
    }
}
