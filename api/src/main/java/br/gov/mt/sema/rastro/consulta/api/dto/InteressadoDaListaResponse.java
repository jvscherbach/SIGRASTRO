package br.gov.mt.sema.rastro.consulta.api.dto;

import br.gov.mt.sema.rastro.consulta.domain.Interessado;

/**
 * Interessado como aparece nas listagens: só o nome (pessoa) ou a razão social (empresa). Nunca CPF ou CNPJ, nem
 * mascarado (S11, D97); o documento só aparece no detalhe do requerimento.
 */
public record InteressadoDaListaResponse(String nome) {

    public static InteressadoDaListaResponse de(Interessado interessado) {
        return interessado == null ? null : new InteressadoDaListaResponse(interessado.nome());
    }
}
