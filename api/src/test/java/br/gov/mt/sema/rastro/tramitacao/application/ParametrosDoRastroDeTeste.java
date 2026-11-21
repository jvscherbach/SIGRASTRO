package br.gov.mt.sema.rastro.tramitacao.application;

import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;

/** Parâmetros mutáveis para os testes: a mudança vale na próxima leitura, como na aplicação. */
final class ParametrosDoRastroDeTeste implements ParametrosDoRastro {

    volatile boolean mostrarNome = true;
    volatile int maximoDeNomes = 10;
    volatile int cacheSegundos;

    @Override
    public boolean mostrarNomeDoResponsavel() {
        return mostrarNome;
    }

    @Override
    public int maximoDeNomesPorEquipe() {
        return maximoDeNomes;
    }

    @Override
    public int cacheDaPosicaoSegundos() {
        return cacheSegundos;
    }
}
