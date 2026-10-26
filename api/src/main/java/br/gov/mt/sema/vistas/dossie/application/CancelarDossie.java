package br.gov.mt.sema.vistas.dossie.application;

import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import org.springframework.stereotype.Service;

/** Cancela o pedido em andamento ou descarta o arquivo já gerado. Idempotente. */
@Service
public class CancelarDossie {

    private final LocalizarPedido localizar;

    public CancelarDossie(LocalizarPedido localizar) {
        this.localizar = localizar;
    }

    public void executar(String id) {
        PedidoDeDossie pedido = localizar.executar(id);
        ArquivosTemporarios.apagarArquivo(pedido.cancelar());
    }
}
