package br.gov.mt.sema.rastro.configuracao.infrastructure.memoria;

import java.util.Map;

import br.gov.mt.sema.rastro.configuracao.application.port.RepositorioDeSobreposicoes;
import org.springframework.stereotype.Repository;

/** Somem ao reiniciar: os parâmetros voltam aos valores do ambiente (D09). */
// S5: persistência — na versão definitiva, tabela de parâmetros no schema próprio (docs/12).
@Repository
class RepositorioDeSobreposicoesEmMemoria implements RepositorioDeSobreposicoes {

    private volatile Map<String, String> sobreposicoes = Map.of();

    @Override
    public Map<String, String> todas() {
        return sobreposicoes;
    }

    @Override
    public void substituir(Map<String, String> novas) {
        sobreposicoes = Map.copyOf(novas);
    }
}
