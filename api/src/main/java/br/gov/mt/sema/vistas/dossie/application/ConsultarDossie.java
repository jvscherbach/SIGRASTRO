package br.gov.mt.sema.vistas.dossie.application;

import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import org.springframework.stereotype.Service;

@Service
public class ConsultarDossie {

    private final LocalizarPedido localizar;
    private final ParametrosDoDossie parametros;

    public ConsultarDossie(LocalizarPedido localizar, ParametrosDoDossie parametros) {
        this.localizar = localizar;
        this.parametros = parametros;
    }

    public AndamentoDoDossie executar(String id) {
        return AndamentoDoDossie.de(localizar.executar(id), parametros.blobMaxMb() * 1024L * 1024L);
    }
}
