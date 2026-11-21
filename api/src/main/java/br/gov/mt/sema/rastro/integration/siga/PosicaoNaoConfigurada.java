package br.gov.mt.sema.rastro.integration.siga;

import java.util.Optional;

import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Sem SIGA_DB_URL a posição não tem de onde ler. Uma classe por porta (e nenhuma junto de {@link SigaNaoConfigurado}):
 * um teste que troca uma porta por dublê não leva as outras junto.
 */
@Component
@ConditionalOnExpression(SigaCondicoes.NAO_CONFIGURADO)
class PosicaoNaoConfigurada implements PosicaoNoFluxo {

    @Override
    public Optional<RequerimentoNoFluxo> requerimento(long idRequerimento) {
        throw new SigaIndisponivelException();
    }

    @Override
    public LeituraDoFluxo ler(long idRequerimento, String chaveDoModelo) {
        throw new SigaIndisponivelException();
    }
}
