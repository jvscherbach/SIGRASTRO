package br.gov.mt.sema.rastro.dossie.application;

import br.gov.mt.sema.rastro.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.rastro.dossie.domain.PedidoDeDossie;
import org.springframework.stereotype.Service;

/**
 * Cancela o pedido em qualquer situação não terminal. Fora da obtenção e da montagem, apaga na hora o diretório do
 * pedido (documentos já obtidos e o PDF final); em andamento, interrompe a conversão em curso (S3) e o executor para no
 * próximo passo e o apaga. Idempotente.
 */
@Service
public class CancelarDossie {

    private final LocalizarPedido localizar;
    private final ParametrosDoDossie parametros;
    private final GeradorDeDossie gerador;

    public CancelarDossie(LocalizarPedido localizar, ParametrosDoDossie parametros, GeradorDeDossie gerador) {
        this.localizar = localizar;
        this.parametros = parametros;
        this.gerador = gerador;
    }

    public void executar(String id) {
        PedidoDeDossie pedido = localizar.executar(id);
        if (pedido.cancelar()) {
            ArquivosTemporarios.apagarPasta(ArquivosTemporarios.pastaDo(parametros, pedido));
        } else {
            // Em andamento: uma conversão longa não espera terminar; o executor apaga o diretório.
            gerador.interromperConversao(pedido.id());
        }
    }
}
