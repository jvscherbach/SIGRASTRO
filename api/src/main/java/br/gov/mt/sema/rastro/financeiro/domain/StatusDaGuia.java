package br.gov.mt.sema.rastro.financeiro.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * {@code LANCAMENTO_TAXA.STATUS} do Licenciamento (S9, decisão do prompt; docs/09). O Rastro mostra o status como está e
 * nunca o troca (a "Taxa vencida" por data é um aviso acrescentado, bloco 3).
 */
public enum StatusDaGuia {
    AGUARDANDO_EMISSAO,
    AGUARDANDO_PAGAMENTO,
    PAGA,
    VENCIDA,
    ISENTA,
    CANCELADA;

    /**
     * O status pelo texto gravado no SIGA, só com os espaços das pontas removidos. Qualquer outra grafia (minúsculas,
     * valor novo do enum, nulo) é "desconhecido": o lançamento é mantido com aviso, nunca adivinhado.
     */
    public static Optional<StatusDaGuia> de(String bruto) {
        if (bruto == null) {
            return Optional.empty();
        }
        String texto = bruto.strip();
        return Arrays.stream(values()).filter(s -> s.name().equals(texto)).findFirst();
    }
}
