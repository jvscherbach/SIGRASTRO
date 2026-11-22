package br.gov.mt.sema.rastro.tramitacao.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;

/**
 * O que a tramitação mostra do financeiro (S9, bloco 5), por porta: implementada fora da tramitação (módulo
 * {@code financeiro}), para a tramitação não conhecer taxa, DAR nem {@code LANCAMENTO_TAXA}. Só <b>acrescenta</b>: o aviso
 * de taxa vencida na posição e as marcas nas passagens; nunca muda etapa, situação nem caminho.
 *
 * <p>Toda implementação é tolerante: falha do financeiro devolve "nada a acrescentar" (mapa vazio, listas vazias), nunca
 * exceção, e a posição e a tramitação saem iguais às de sem o financeiro. O acesso já foi conferido por quem chama.
 */
public interface FinanceiroDoRequerimento {

    /** Sem financeiro: nada a acrescentar (dublê de teste e reserva). */
    FinanceiroDoRequerimento NENHUM = new FinanceiroDoRequerimento() {
        @Override
        public Map<Long, TaxaVencida> taxasVencidas(Collection<Long> idsRequerimento) {
            return Map.of();
        }

        @Override
        public List<List<MarcaFinanceira>> marcas(long idRequerimento, List<PassagemPelaEtapa> passagens) {
            return passagens.stream().map(p -> List.<MarcaFinanceira>of()).toList();
        }
    };

    /**
     * Os requerimentos (entre os pedidos) com o resumo financeiro {@code VENCIDO}; os demais ficam fora do mapa. Uma leitura
     * em lote para todos.
     */
    Map<Long, TaxaVencida> taxasVencidas(Collection<Long> idsRequerimento);

    /** Uma lista de marcas por passagem, mesma ordem e tamanho de {@code passagens} (só do caminho exato). */
    List<List<MarcaFinanceira>> marcas(long idRequerimento, List<PassagemPelaEtapa> passagens);

    /**
     * @param vencidaEm data de vencimento da taxa vencida mais recente; vazia se o SIGA não a gravou
     * @param porData   vencida pela data (o SIGA ainda mostra "aguardando pagamento"), e não pela marca {@code VENCIDA}
     */
    record TaxaVencida(Optional<LocalDate> vencidaEm, boolean porData) {}

    /**
     * Marca pronta para exibir ao lado de uma passagem.
     *
     * @param tipo           código estável ({@code EMITIDA_EM}, {@code NUMERO_DAR}, {@code VENCE_EM}, {@code PAGA_EM},
     *                       {@code TAXA_VENCIDA}, {@code ISENTO_DE_TAXA}, {@code DA_CORRECAO})
     * @param rotulo         texto ao usuário ("Emitida em", "DAR", "Isento de Taxa"...)
     * @param data           data pura, quando a marca é de data
     * @param valor          o número do DAR, quando é o DAR
     * @param dataAConfirmar só no "Isento de Taxa" cuja data é provisória
     */
    record MarcaFinanceira(String tipo, String rotulo, Optional<LocalDate> data, Optional<String> valor,
            boolean dataAConfirmar) {}
}
