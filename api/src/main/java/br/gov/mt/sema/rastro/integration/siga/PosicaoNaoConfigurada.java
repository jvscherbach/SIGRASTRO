package br.gov.mt.sema.rastro.integration.siga;

import java.util.Optional;

import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Sem SIGA_DB_URL a posição não tem de onde ler. Em classe própria (e não em {@link SigaNaoConfigurado}) para que os
 * testes que trocam aquele bean por um dublê não levem a porta da tramitação junto.
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
