package br.gov.mt.sema.rastro.integration.siga;

import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import br.gov.mt.sema.rastro.tramitacao.application.port.CaminhoPercorrido;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoBruto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Sem SIGA_DB_URL o caminho percorrido não tem de onde ler (uma classe por porta, como {@link PosicaoNaoConfigurada}). */
@Component
@ConditionalOnExpression(SigaCondicoes.NAO_CONFIGURADO)
class CaminhoNaoConfigurado implements CaminhoPercorrido {

    @Override
    public CaminhoBruto lerCaminho(long idRequerimento, String chaveDoModelo) {
        throw new SigaIndisponivelException();
    }
}
