package br.gov.mt.sema.vistas.consulta.api.dto;

import br.gov.mt.sema.vistas.consulta.domain.Identificacao;

/** RN04 já resolvida: "Processo" com o número do processo, ou "Requerimento" enquanto não protocolado. */
public record IdentificacaoResponse(String rotulo, String numero, boolean protocolado) {

    public static IdentificacaoResponse de(Identificacao identificacao) {
        return new IdentificacaoResponse(identificacao.rotulo(), identificacao.numero(), identificacao.protocolado());
    }
}
