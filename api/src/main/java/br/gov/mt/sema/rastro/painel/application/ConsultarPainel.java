package br.gov.mt.sema.rastro.painel.application;

import java.time.Instant;
import java.util.Optional;

import br.gov.mt.sema.rastro.painel.domain.EstadoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.InstantaneoDoPainel;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import org.springframework.stereotype.Service;

/**
 * Leitura do painel (S10, bloco 4). Só lê o instantâneo em memória: <b>nunca</b> chama as portas do SIGA nem do Camunda.
 * A primeira requisição dispara a primeira leitura em segundo plano e responde {@code PREPARANDO}.
 */
@Service
public class ConsultarPainel {

    /** O que a API devolve: o instantâneo, se os nomes podem aparecer (RN12) e a próxima atualização automática. */
    public record Visao(InstantaneoDoPainel instantaneo, boolean mostrarNome, Optional<Instant> proximaAtualizacaoEm) {
    }

    private final GuardarInstantaneo guardar;
    private final AtualizarPainel atualizar;
    private final ParametrosDoRastro parametrosDoRastro;

    public ConsultarPainel(GuardarInstantaneo guardar, AtualizarPainel atualizar, ParametrosDoRastro parametrosDoRastro) {
        this.guardar = guardar;
        this.atualizar = atualizar;
        this.parametrosDoRastro = parametrosDoRastro;
    }

    public Visao consultar() {
        if (guardar.atual().estado() == EstadoDoPainel.PREPARANDO && atualizar.ultimoInicio().isEmpty()) {
            atualizar.iniciarEmSegundoPlano();
        }
        InstantaneoDoPainel instantaneo = guardar.atual();
        Optional<Instant> proxima = atualizar.ultimoInicio().map(i -> i.plus(atualizar.intervaloAutomatico()));
        return new Visao(instantaneo, parametrosDoRastro.mostrarNomeDoResponsavel(), proxima);
    }
}
