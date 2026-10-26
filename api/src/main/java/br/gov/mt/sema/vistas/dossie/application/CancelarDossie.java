package br.gov.mt.sema.vistas.dossie.application;

import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import org.springframework.stereotype.Service;

/**
 * Cancela o pedido em qualquer situação não terminal. Fora da obtenção e da montagem, apaga na hora o diretório do
 * pedido (documentos já obtidos e o PDF final); em andamento, o executor para no próximo passo e o apaga. Idempotente.
 */
@Service
public class CancelarDossie {

    private final LocalizarPedido localizar;
    private final ParametrosDoDossie parametros;

    public CancelarDossie(LocalizarPedido localizar, ParametrosDoDossie parametros) {
        this.localizar = localizar;
        this.parametros = parametros;
    }

    public void executar(String id) {
        PedidoDeDossie pedido = localizar.executar(id);
        if (pedido.cancelar()) {
            ArquivosTemporarios.apagarPasta(ArquivosTemporarios.pastaDo(parametros, pedido));
        }
    }
}
