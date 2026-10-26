package br.gov.mt.sema.vistas.integration.licenciamento;

import java.nio.file.Path;

import br.gov.mt.sema.vistas.dossie.application.port.ArquivosDoRepositorio;
import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Sem LICENCIAMENTO_API_URL a aplicação sobe; só a geração falha, com o motivo explícito. A listagem não depende da API. */
@Component
@ConditionalOnExpression(ArquivosCondicoes.NAO_CONFIGURADO)
class ArquivosNaoConfigurados implements ArquivosDoRepositorio {

    static final String MOTIVO = "API de arquivos não configurada";

    @Override
    public int baixar(String chave, Path destino) {
        throw new FalhaNoDocumentoException(MOTIVO, 0);
    }
}
