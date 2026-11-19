package br.gov.mt.sema.rastro.dossie.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.rastro.acesso.application.PedidoDeUsuario;
import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.acesso.application.UsuariosAtivos;
import br.gov.mt.sema.rastro.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.rastro.dossie.application.port.RepositorioDePedidos;
import br.gov.mt.sema.rastro.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
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
    private final UsuariosAtivos usuarios;

    public LocalizarPedido(RepositorioDePedidos pedidos, PoliticaDeAcesso politica, ParametrosDoDossie parametros,
            Clock relogio, UsuariosAtivos usuarios) {
        this.pedidos = pedidos;
        this.politica = politica;
        this.parametros = parametros;
        this.relogio = relogio;
        this.usuarios = usuarios;
    }

    public PedidoDeDossie executar(String id) {
        PedidoDeDossie pedido = pedidos.buscar(id == null ? "" : id)
                .filter(p -> politica.podeAcessarPedido(resumo(p)))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dossiê não encontrado."));
        return expirarSeVenceu(pedido);
    }

    /** Todos os pedidos que o usuário da requisição acessa (mesma regra de {@link #executar}), já expirados se venceram. */
    public List<PedidoDeDossie> visiveis() {
        return pedidos.todos().stream()
                .filter(p -> politica.podeAcessarPedido(resumo(p)))
                .map(this::expirarSeVenceu)
                .toList();
    }

    /**
     * Sem usuário na requisição: só para quem já provou o acesso de outro jeito (ticket de download, D49), que foi
     * emitido depois de {@link #executar}. Quem usa o ticket confere {@link #titularAcessa} antes de entregar.
     */
    PedidoDeDossie semUsuario(String id) {
        return expirarSeVenceu(pedidos.buscar(id == null ? "" : id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dossiê não encontrado.")));
    }

    /** O dono do ticket ainda acessa o pedido agora: quem foi desativado ou rebaixado depois da emissão, não (R53). */
    boolean titularAcessa(PedidoDeDossie pedido, String login) {
        return usuarios.porLogin(login).filter(u -> politica.podeAcessarPedido(u, resumo(pedido))).isPresent();
    }

    private static PedidoDeUsuario resumo(PedidoDeDossie pedido) {
        return new PedidoDeUsuario(pedido.login(), pedido.capa().cpfCnpj(), pedido.contemSigiloso());
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
