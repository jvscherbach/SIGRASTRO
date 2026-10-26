package br.gov.mt.sema.vistas.dossie.application;

import org.springframework.stereotype.Service;

@Service
public class ConsultarDossie {

    private final LocalizarPedido localizar;

    public ConsultarDossie(LocalizarPedido localizar) {
        this.localizar = localizar;
    }

    public AndamentoDoDossie executar(String id) {
        return AndamentoDoDossie.de(localizar.executar(id));
    }
}
