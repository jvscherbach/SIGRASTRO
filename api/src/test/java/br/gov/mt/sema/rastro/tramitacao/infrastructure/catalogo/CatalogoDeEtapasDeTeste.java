package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;

/**
 * Dá aos testes de outros pacotes (ex.: o {@code CamundaFicticioBuilder} de {@code integration.siga}) o catálogo de etapas
 * real, lido de {@code rastro/etapas/*.yml}, sem subir o Spring. Assim as fixtures usam exatamente os ids do catálogo.
 */
public final class CatalogoDeEtapasDeTeste {

    private static final CatalogoDeEtapas PADRAO = new CatalogoDeEtapasEmArquivo();

    private CatalogoDeEtapasDeTeste() {
    }

    public static CatalogoDeEtapas padrao() {
        return PADRAO;
    }
}
