package br.gov.mt.sema.vistas.dossie.application;

import java.time.Clock;
import java.time.LocalDateTime;

import br.gov.mt.sema.vistas.acesso.application.PedidoDeUsuario;
import br.gov.mt.sema.vistas.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.application.port.RepositorioDePedidos;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.shared.application.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;

/**
 * Localiza o pedido para o usuário da requisição. Quem o acessa é decidido pela política de acesso (quem o criou,
 * enquanto tiver acesso ao requerimento, além de Gestor e Administrador); para os demais ele não existe (404), o que
 * também não revela se o id é válido. Pedido concluído ou à espera de decisão e
 * vencido (sem atividade dentro do TTL) é expirado aqui, com o diretório apagado,
 * sem esperar a limpeza agendada.
 */
@Service
public class LocalizarPedido {

    private final RepositorioDePedidos pedidos;
    private final PoliticaDeAcesso politica;
    private final ParametrosDoDossie parametros;
    private final Clock relogio;

    public LocalizarPedido(RepositorioDePedidos pedidos, PoliticaDeAcesso politica, ParametrosDoDossie parametros,
            Clock relogio) {
        this.pedidos = pedidos;
        this.politica = politica;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    public PedidoDeDossie executar(String id) {
        PedidoDeDossie pedido = pedidos.buscar(id == null ? "" : id)
                .filter(p -> politica.podeAcessarPedido(new PedidoDeUsuario(p.login(), p.capa().cpfCnpj())))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dossiê não encontrado."));
        return expirarSeVenceu(pedido);
    }

    /**
     * Sem usuário na requisição: só para quem já provou o acesso de outro jeito (ticket de download, D49), que foi
     * emitido depois de {@link #executar}.
     */
    PedidoDeDossie semUsuario(String id) {
        return expirarSeVenceu(pedidos.buscar(id == null ? "" : id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dossiê não encontrado.")));
    }

    private PedidoDeDossie expirarSeVenceu(PedidoDeDossie pedido) {
        if (pedido.venceu(LocalDateTime.now(relogio))) {
            if (pedido.expirar()) {
                ArquivosTemporarios.apagarPasta(ArquivosTemporarios.pastaDo(parametros, pedido));
            }
        }
        return pedido;
    }
}
