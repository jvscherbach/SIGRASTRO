package br.gov.mt.sema.rastro.painel.application;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import br.gov.mt.sema.rastro.painel.domain.InstantaneoDoPainel;
import org.springframework.stereotype.Component;

/** O instantâneo vigente do painel, numa referência atômica (S10): ler nunca bloqueia e a troca é de uma vez. */
@Component
public class GuardarInstantaneo {

    private final AtomicReference<InstantaneoDoPainel> atual = new AtomicReference<>(InstantaneoDoPainel.PREPARANDO);

    public InstantaneoDoPainel atual() {
        return atual.get();
    }

    public void trocar(InstantaneoDoPainel novo) {
        atual.set(novo);
    }

    /** A atualização falhou: mantém o último bom (DESATUALIZADO) ou fica INDISPONIVEL. */
    public void marcarFalha(Instant quando) {
        atual.updateAndGet(i -> i.aposFalha(quando));
    }
}
