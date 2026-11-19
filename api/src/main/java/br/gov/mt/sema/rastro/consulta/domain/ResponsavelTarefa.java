package br.gov.mt.sema.rastro.consulta.domain;

/**
 * Responsável pela tarefa atual. Só o nome sai do adapter: o CPF do servidor nunca entra no domínio.
 * O nome existe apenas na situação {@code ATRIBUIDO}.
 */
public record ResponsavelTarefa(Situacao situacao, String nome) {

    public enum Situacao {
        ATRIBUIDO,
        NAO_ATRIBUIDA,
        NAO_IDENTIFICADO
    }

    public static ResponsavelTarefa atribuido(String nome) {
        if (nome == null || nome.isBlank()) {
            return naoIdentificado();
        }
        return new ResponsavelTarefa(Situacao.ATRIBUIDO, nome.trim());
    }

    public static ResponsavelTarefa naoAtribuida() {
        return new ResponsavelTarefa(Situacao.NAO_ATRIBUIDA, null);
    }

    public static ResponsavelTarefa naoIdentificado() {
        return new ResponsavelTarefa(Situacao.NAO_IDENTIFICADO, null);
    }
}
