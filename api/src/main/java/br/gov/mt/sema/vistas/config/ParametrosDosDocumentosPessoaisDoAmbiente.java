package br.gov.mt.sema.vistas.config;

import br.gov.mt.sema.vistas.documento.application.port.ParametrosDosDocumentosPessoais;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** {@code SCORP_SIGILO_PADRAO} (S4, P30) vindo de variável de ambiente (protótipo). */
@Component
class ParametrosDosDocumentosPessoaisDoAmbiente implements ParametrosDosDocumentosPessoais {

    private final boolean sigiloPadrao;

    ParametrosDosDocumentosPessoaisDoAmbiente(@Value("${vistas.scorp.sigilo-padrao:false}") boolean sigiloPadrao) {
        this.sigiloPadrao = sigiloPadrao;
    }

    @Override
    public boolean sigiloPadrao() {
        return sigiloPadrao;
    }
}
