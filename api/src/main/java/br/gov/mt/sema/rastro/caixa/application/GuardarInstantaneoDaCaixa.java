package br.gov.mt.sema.rastro.caixa.application;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import br.gov.mt.sema.rastro.caixa.domain.InstantaneoDaCaixa;
import org.springframework.stereotype.Component;

/** O instantâneo vigente da caixa, numa referência atômica (S11): ler nunca bloqueia e a troca é de uma vez. */
@Component
public class GuardarInstantaneoDaCaixa {

    private final AtomicReference<InstantaneoDaCaixa> atual = new AtomicReference<>(InstantaneoDaCaixa.PREPARANDO);

    public InstantaneoDaCaixa atual() {
        return atual.get();
    }

    public void trocar(InstantaneoDaCaixa novo) {
        atual.set(novo);
    }

    /** A atualização falhou: mantém o último bom (DESATUALIZADO) ou fica INDISPONIVEL. */
    public void marcarFalha(Instant quando) {
        atual.updateAndGet(i -> i.aposFalha(quando));
    }
}
