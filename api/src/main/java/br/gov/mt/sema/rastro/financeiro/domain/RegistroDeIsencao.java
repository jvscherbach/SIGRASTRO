package br.gov.mt.sema.rastro.financeiro.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Uma entrada "Isento de Taxa" (S9, bloco 4): uma por lançamento {@code ISENTA} válido, em ordem. A data vem da cadeia de
 * apoio de {@link DataDaIsencao}; {@code origemDaData} diz de onde (diagnóstico e nota "data a confirmar").
 */
public record RegistroDeIsencao(LancamentoAvaliado lancamento, Optional<LocalDate> data, OrigemDaData origemDaData) {

    public static final String TEXTO = "Isento de Taxa";

    public RegistroDeIsencao {
        if (data.isEmpty() != (origemDaData == OrigemDaData.AUSENTE)) {
            throw new IllegalArgumentException("data ausente só com origem AUSENTE");
        }
    }

    public String texto() {
        return TEXTO;
    }

    /** A data não é a do próprio lançamento (veio do fluxo ou não existe): a tela marca "data a confirmar". */
    public boolean dataAConfirmar() {
        return origemDaData != OrigemDaData.LANCAMENTO;
    }

    /**
     * {@code STATUS_PROCESSADO = N} no lançamento isento: sem tratamento especial (o "Isento de Taxa" aparece igual); só nota
     * no diagnóstico do Administrador (docs/10, P43).
     */
    public boolean naoProcessado() {
        return "N".equals(lancamento.lancamento().statusProcessado());
    }
}
