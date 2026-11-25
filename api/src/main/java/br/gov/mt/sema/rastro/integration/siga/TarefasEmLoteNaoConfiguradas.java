package br.gov.mt.sema.rastro.integration.siga;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import br.gov.mt.sema.rastro.painel.application.port.TarefasAtivasEmLote;
import br.gov.mt.sema.rastro.painel.domain.LeituraDasTarefas;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Sem SIGA_DB_URL o painel não lê o Camunda (S10). Uma classe por porta, como {@link LancamentosNaoConfigurados}. */
@Component
@ConditionalOnExpression(SigaCondicoes.NAO_CONFIGURADO)
class TarefasEmLoteNaoConfiguradas implements TarefasAtivasEmLote {

    @Override
    public LeituraDasTarefas ler(List<Long> idsRequerimento, Collection<String> chavesDosModelos, Duration tempoLimite) {
        throw new SigaIndisponivelException();
    }
}
