package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDoConfronto;

/** Dá aos testes de outros pacotes o catálogo do confronto real ({@code rastro/confronto.yaml}), sem subir o Spring. */
public final class CatalogoDoConfrontoDeTeste {

    private static final CatalogoDoConfronto PADRAO = new CatalogoDoConfrontoEmArquivo(CatalogoDeEtapasDeTeste.padrao());

    private CatalogoDoConfrontoDeTeste() {
    }

    public static CatalogoDoConfronto padrao() {
        return PADRAO;
    }
}
