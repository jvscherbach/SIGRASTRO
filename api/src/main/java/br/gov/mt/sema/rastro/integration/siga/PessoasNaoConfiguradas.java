package br.gov.mt.sema.rastro.integration.siga;

import java.util.List;

import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Sem SIGA_DB_URL: como as demais leituras, 503 SIGA_INDISPONIVEL (a lista de documentos já falhou antes). */
@Component
@ConditionalOnExpression(SigaCondicoes.NAO_CONFIGURADO)
class PessoasNaoConfiguradas implements PessoasDoRequerimento {

    @Override
    public List<Pessoa> listarPessoas(long idRequerimento) {
        throw new SigaIndisponivelException();
    }
}
