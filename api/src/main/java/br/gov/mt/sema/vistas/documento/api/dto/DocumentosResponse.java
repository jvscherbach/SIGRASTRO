package br.gov.mt.sema.vistas.documento.api.dto;

import java.util.List;

import br.gov.mt.sema.vistas.documento.application.DocumentosListados;

/** {@code geometrias} (S4) fica fora de {@code itens} e da conta de {@code total}; {@code totais.geometrias} é o tamanho dela. */
public record DocumentosResponse(List<DocumentoResponse> itens, TotaisResponse totais, List<GeometriaResponse> geometrias,
        List<AvisoResponse> avisos) {

    /** S4: o que não impediu a listagem (ex.: {@code SCORP_INDISPONIVEL}, {@code SCORP_PARCIAL}); sem detalhe técnico. */
    public record AvisoResponse(String codigo, String mensagem) {
    }

    public record TotaisResponse(int total, int vigentes, int semEfeito, int naoPdf, int tipoNaoInformado,
            int indisponiveis, int sigilosos, int convertiveis, int geometrias, int geometriasOmitidas) {
    }

    public static DocumentosResponse de(DocumentosListados listados) {
        DocumentosListados.Totais t = listados.totais();
        return new DocumentosResponse(listados.itens().stream().map(DocumentoResponse::de).toList(),
                new TotaisResponse(t.total(), t.vigentes(), t.semEfeito(), t.naoPdf(), t.tipoNaoInformado(),
                        t.indisponiveis(), t.sigilosos(), t.convertiveis(), listados.geometrias().size(),
                        listados.geometriasOmitidas()),
                listados.geometrias().stream().map(GeometriaResponse::de).toList(),
                listados.avisos().stream().map(a -> new AvisoResponse(a.codigo(), a.mensagem())).toList());
    }
}
