package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import br.gov.mt.sema.rastro.tramitacao.domain.MapaDeEventosDoHistorico;

/** Dá aos testes de outros pacotes o mapa de eventos real ({@code rastro/eventos-historico.yaml}), sem subir o Spring. */
public final class MapaDeEventosDeTeste {

    private static final MapaDeEventosDoHistorico PADRAO = new MapaDeEventosEmArquivo(CatalogoDeEtapasDeTeste.padrao());

    private MapaDeEventosDeTeste() {
    }

    public static MapaDeEventosDoHistorico padrao() {
        return PADRAO;
    }
}
