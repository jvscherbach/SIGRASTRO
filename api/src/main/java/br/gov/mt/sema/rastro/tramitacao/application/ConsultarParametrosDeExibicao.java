package br.gov.mt.sema.rastro.tramitacao.application;

import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import org.springframework.stereotype.Service;

/**
 * Parâmetros que a tela usa para pedir posições (S7, bloco 10), para qualquer usuário autenticado: só números de
 * comportamento da interface, sem credencial, URL ou dado de negócio. Lidos a cada chamada (classe "imediato").
 */
@Service
public class ConsultarParametrosDeExibicao {

    private final ParametrosDoRastro parametros;

    public ConsultarParametrosDeExibicao(ParametrosDoRastro parametros) {
        this.parametros = parametros;
    }

    public ParametrosDeExibicao consultar() {
        return new ParametrosDeExibicao(parametros.posicoesSimultaneas());
    }

    /** @param posicoesSimultaneas quantas posições a tela pede ao mesmo tempo (lista da busca, processo) */
    public record ParametrosDeExibicao(int posicoesSimultaneas) {}
}
