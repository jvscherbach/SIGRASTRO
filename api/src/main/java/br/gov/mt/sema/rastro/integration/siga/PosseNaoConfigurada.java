package br.gov.mt.sema.rastro.integration.siga;

import java.time.Duration;
import java.util.List;

import br.gov.mt.sema.rastro.caixa.application.port.PosseDoAnalistaEmLote;
import br.gov.mt.sema.rastro.caixa.domain.AnalistaParaLeitura;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.LeituraDaPosse;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Sem SIGA_DB_URL a caixa não lê o Camunda (S11). Uma classe por porta, como {@link TarefasEmLoteNaoConfiguradas}. */
@Component
@ConditionalOnExpression(SigaCondicoes.NAO_CONFIGURADO)
class PosseNaoConfigurada implements PosseDoAnalistaEmLote {

    @Override
    public List<Long> candidatos(List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao, int maximo,
            Duration tempoLimite) {
        throw new SigaIndisponivelException();
    }

    @Override
    public LeituraDaPosse ler(List<Long> ids, List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao,
            Duration tempoLimite) {
        throw new SigaIndisponivelException();
    }
}
