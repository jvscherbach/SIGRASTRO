package br.gov.mt.sema.vistas.documento.api.dto;

import java.util.List;

import br.gov.mt.sema.vistas.documento.application.DocumentosListados;

public record DocumentosResponse(List<DocumentoResponse> itens, TotaisResponse totais) {

    public record TotaisResponse(int total, int vigentes, int semEfeito, int naoPdf, int tipoNaoInformado,
            int indisponiveis, int sigilosos) {
    }

    public static DocumentosResponse de(DocumentosListados listados) {
        DocumentosListados.Totais t = listados.totais();
        return new DocumentosResponse(listados.itens().stream().map(DocumentoResponse::de).toList(),
                new TotaisResponse(t.total(), t.vigentes(), t.semEfeito(), t.naoPdf(), t.tipoNaoInformado(), t.indisponiveis(), t.sigilosos()));
    }
}
