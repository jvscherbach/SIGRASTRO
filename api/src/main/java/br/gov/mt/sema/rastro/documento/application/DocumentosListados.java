package br.gov.mt.sema.rastro.documento.application;

import java.util.List;

import br.gov.mt.sema.rastro.documento.domain.SituacaoDocumento;

/**
 * Lista do requerimento. {@code geometrias} (RN10, S4) fica fora de {@code itens} e de {@code totais}: só as que o perfil
 * acessa; {@code geometriasOmitidas} conta as sigilosas que o Operador não vê (sem nome nem data). {@code avisos} (S4):
 * o que não impediu a listagem, como o SCORP fora do ar.
 */
public record DocumentosListados(List<DocumentoListado> itens, Totais totais, List<DocumentoListado> geometrias,
        int geometriasOmitidas, List<AvisoDaLista> avisos) {

    /**
     * {@code total = vigentes + semEfeito + indisponiveis} (situação, excludentes). {@code naoPdf},
     * {@code tipoNaoInformado}, {@code convertiveis} e {@code sigilosos} são recortes que cruzam essas situações.
     * {@code convertiveis} conta os documentos convertíveis para PDF que o perfil pode selecionar (sem bloqueio);
     * com a conversão desligada é sempre 0.
     */
    public record Totais(int total, int vigentes, int semEfeito, int naoPdf, int tipoNaoInformado,
            int indisponiveis, int sigilosos, int convertiveis) {
    }

    static DocumentosListados de(List<DocumentoListado> itens, List<DocumentoListado> geometrias, int geometriasOmitidas,
            List<AvisoDaLista> avisos) {
        int vigentes = 0;
        int semEfeito = 0;
        int naoPdf = 0;
        int tipoNaoInformado = 0;
        int indisponiveis = 0;
        int sigilosos = 0;
        int convertiveis = 0;
        for (DocumentoListado item : itens) {
            if (item.documento().situacao() == SituacaoDocumento.VIGENTE) {
                vigentes++;
            }
            if (item.documento().situacao() == SituacaoDocumento.SUBSTITUIDO) {
                semEfeito++;
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
            if (item.bloqueio() == null && item.documento().convertivel()) {
                convertiveis++;
            }
        }
        return new DocumentosListados(List.copyOf(itens), new Totais(itens.size(), vigentes, semEfeito, naoPdf,
                tipoNaoInformado, indisponiveis, sigilosos, convertiveis), List.copyOf(geometrias), geometriasOmitidas,
                List.copyOf(avisos));
    }
}
