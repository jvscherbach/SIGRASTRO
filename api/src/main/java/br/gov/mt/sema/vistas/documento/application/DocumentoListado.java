package br.gov.mt.sema.vistas.documento.application;

import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.Documento.Bloqueio;

/**
 * Documento como a lista o entrega ao usuário. Para sigiloso sem permissão o título é mascarado e o nome do
 * arquivo e a extensão não saem.
 */
public record DocumentoListado(Documento documento, boolean acessivel, Bloqueio bloqueio, boolean selecionavelPorPadrao) {

    public static final String TITULO_MASCARADO = "Documento sigiloso";

    public String titulo() {
        return acessivel ? documento.titulo() : TITULO_MASCARADO;
    }

    public String nomeArquivo() {
        return acessivel ? documento.nomeArquivo() : null;
    }

    public String extensao() {
        return acessivel ? documento.extensao() : null;
    }
}
