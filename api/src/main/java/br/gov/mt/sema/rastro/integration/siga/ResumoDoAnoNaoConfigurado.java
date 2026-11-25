package br.gov.mt.sema.rastro.integration.siga;

import java.time.Duration;

import br.gov.mt.sema.rastro.painel.application.port.ResumoDoAnoNoSiga;
import br.gov.mt.sema.rastro.painel.domain.AnoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.BaseDoAno;
import br.gov.mt.sema.rastro.painel.domain.LeituraDaSerie;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Sem SIGA_DB_URL o painel não tem de onde ler: a atualização falha e, sem leitura boa, o painel fica
 * {@code INDISPONIVEL} (S10). Uma classe por porta, como {@link LancamentosNaoConfigurados}.
 */
@Component
@ConditionalOnExpression(SigaCondicoes.NAO_CONFIGURADO)
class ResumoDoAnoNaoConfigurado implements ResumoDoAnoNoSiga {

    @Override
    public LeituraDaSerie lerSerie(AnoDoPainel ano, Duration tempoLimite) {
        throw new SigaIndisponivelException();
    }

    @Override
    public BaseDoAno lerBase(AnoDoPainel ano, int limite, Duration tempoLimite) {
        throw new SigaIndisponivelException();
    }
}
