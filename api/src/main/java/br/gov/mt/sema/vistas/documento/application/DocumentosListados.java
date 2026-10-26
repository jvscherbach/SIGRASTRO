package br.gov.mt.sema.vistas.documento.application;

import java.util.List;

import br.gov.mt.sema.vistas.documento.domain.SituacaoDocumento;

public record DocumentosListados(List<DocumentoListado> itens, Totais totais) {

    public record Totais(int total, int vigentes, int naoPdf, int tipoNaoInformado, int indisponiveis,
            int sigilosos) {
    }

    static DocumentosListados de(List<DocumentoListado> itens) {
        int vigentes = 0;
        int naoPdf = 0;
        int tipoNaoInformado = 0;
        int indisponiveis = 0;
        int sigilosos = 0;
        for (DocumentoListado item : itens) {
            if (item.documento().situacao() == SituacaoDocumento.VIGENTE) {
                vigentes++;
            }
            if (item.documento().situacao() == SituacaoDocumento.INDISPONIVEL) {
                indisponiveis++;
            }
            if (item.documento().tipoNaoInformado()) {
                tipoNaoInformado++;
            } else if (!item.documento().ehPdf()) {
                naoPdf++;
            }
            if (item.documento().sigiloso()) {
                sigilosos++;
            }
        }
        return new DocumentosListados(List.copyOf(itens),
                new Totais(itens.size(), vigentes, naoPdf, tipoNaoInformado, indisponiveis, sigilosos));
    }
}
