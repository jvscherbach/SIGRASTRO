package br.gov.mt.sema.rastro.integration.arrecadacao;

import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Taxas seguem desativadas até a forma de obtenção ser confirmada (docs/12). */
@Component
@Order(4)
class VerificadorDeTaxas implements VerificadorDeConexao {

    @Override
    public String nome() {
        return "taxas";
    }

    @Override
    public String rotulo() {
        return "Taxas";
    }

    @Override
    public Estado estado() {
        return Estado.DESATIVADA;
    }

    @Override
    public Resultado verificar() {
        return new Resultado(false, "Conexão desativada.");
    }
}
