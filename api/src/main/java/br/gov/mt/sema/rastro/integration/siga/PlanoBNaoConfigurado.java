package br.gov.mt.sema.rastro.integration.siga;

import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import br.gov.mt.sema.rastro.tramitacao.application.port.HistoricoDoLicenciamento;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoPlanoB;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Sem SIGA_DB_URL o plano B não tem de onde ler (uma classe por porta, como {@link PosicaoNaoConfigurada}). */
@Component
@ConditionalOnExpression(SigaCondicoes.NAO_CONFIGURADO)
class PlanoBNaoConfigurado implements HistoricoDoLicenciamento {

    @Override
    public LeituraDoPlanoB lerPlanoB(long idRequerimento) {
        throw new SigaIndisponivelException();
    }
}
