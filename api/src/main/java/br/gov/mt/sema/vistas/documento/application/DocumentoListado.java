package br.gov.mt.sema.vistas.documento.application;

import br.gov.mt.sema.vistas.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.Documento.Bloqueio;

/**
 * Documento como a lista o entrega ao usuário. Para sigiloso sem permissão o título é mascarado e o nome do
 * arquivo e a extensão não saem.
 */
public record DocumentoListado(Documento documento, boolean acessivel, Bloqueio bloqueio, boolean selecionavelPorPadrao) {

    public static final String TITULO_MASCARADO = PoliticaDeAcesso.TITULO_SIGILOSO;

    public String titulo() {
        return PoliticaDeAcesso.mascararTitulo(acessivel, documento.titulo());
    }

    public String nomeArquivo() {
        return PoliticaDeAcesso.mascararDadoDoArquivo(acessivel, documento.nomeArquivo());
    }

    public String extensao() {
        return PoliticaDeAcesso.mascararDadoDoArquivo(acessivel, documento.extensao());
    }
}
