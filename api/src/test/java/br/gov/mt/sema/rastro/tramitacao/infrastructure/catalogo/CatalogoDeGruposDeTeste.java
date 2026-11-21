package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeGrupos;

/** Dá aos testes de outros pacotes o catálogo de grupos real ({@code rastro/grupos.yaml}), sem subir o Spring. */
public final class CatalogoDeGruposDeTeste {

    private static final CatalogoDeGrupos PADRAO = new CatalogoDeGruposEmArquivo();

    private CatalogoDeGruposDeTeste() {
    }

    public static CatalogoDeGrupos padrao() {
        return PADRAO;
    }
}
