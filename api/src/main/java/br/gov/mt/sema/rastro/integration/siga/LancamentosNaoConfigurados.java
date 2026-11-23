package br.gov.mt.sema.rastro.integration.siga;

import java.util.Collection;
import java.util.Map;

import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Sem SIGA_DB_URL o financeiro não tem de onde ler (uma classe por porta, como {@link PlanoBNaoConfigurado}). */
@Component
@ConditionalOnExpression(SigaCondicoes.NAO_CONFIGURADO)
class LancamentosNaoConfigurados implements LancamentosDoRequerimento {

    @Override
    public LeituraDosLancamentos lerDoRequerimento(long idRequerimento) {
        throw new SigaIndisponivelException();
    }

    @Override
    public Map<Long, LeituraDosLancamentos> lerEmLote(Collection<Long> idsRequerimento) {
        throw new SigaIndisponivelException();
    }
}
