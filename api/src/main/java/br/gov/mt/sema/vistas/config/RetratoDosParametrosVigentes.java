package br.gov.mt.sema.vistas.config;

import java.util.Map;
import java.util.function.Supplier;

import br.gov.mt.sema.vistas.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.vistas.dossie.application.port.RetratoDeParametros;
import org.springframework.stereotype.Component;

@Component
class RetratoDosParametrosVigentes implements RetratoDeParametros {

    private final ParametrosEmVigor vigentes;

    RetratoDosParametrosVigentes(ParametrosEmVigor vigentes) {
        this.vigentes = vigentes;
    }

    @Override
    public Map<String, String> retratar() {
        return vigentes.retrato();
    }

    @Override
    public <T> T comRetrato(Map<String, String> retrato, Supplier<T> tarefa) {
        return vigentes.comRetrato(retrato, tarefa);
    }
}
