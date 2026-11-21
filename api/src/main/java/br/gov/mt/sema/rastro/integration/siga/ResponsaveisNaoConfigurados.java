package br.gov.mt.sema.rastro.integration.siga;

import java.util.Collection;
import java.util.Map;

import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import br.gov.mt.sema.rastro.tramitacao.application.port.IdentificacaoDosResponsaveis;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificacaoDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Sem SIGA_DB_URL não há como identificar o responsável (ver {@link PosicaoNaoConfigurada}). */
@Component
@ConditionalOnExpression(SigaCondicoes.NAO_CONFIGURADO)
class ResponsaveisNaoConfigurados implements IdentificacaoDosResponsaveis {

    @Override
    public Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificar(long idRequerimento,
            Collection<IdentificadorDoResponsavel> responsaveis) {
        throw new SigaIndisponivelException();
    }
}
