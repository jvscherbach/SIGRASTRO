package br.gov.mt.sema.rastro.documento.domain;

import java.time.LocalDateTime;

/** RN01. Indisponível tem precedência: chave nula, vazia ou "0" nunca é requisitada. */
public enum SituacaoDocumento {

    VIGENTE,
    SUBSTITUIDO,
    INDISPONIVEL;

    public static final String CHAVE_INEXISTENTE = "0";

    public static boolean chaveValida(String chave) {
        return chave != null && !chave.isBlank() && !CHAVE_INEXISTENTE.equals(chave.trim());
    }

    /**
     * @param fimDaVigencia valor da coluna de fim (DATA_FIM ou DATA_EXCLUSAO); nulo em origem sem coluna de vigência
     */
    public static SituacaoDocumento calcular(String chave, LocalDateTime fimDaVigencia) {
        if (!chaveValida(chave)) {
            return INDISPONIVEL;
        }
        return fimDaVigencia != null ? SUBSTITUIDO : VIGENTE;
    }
}
