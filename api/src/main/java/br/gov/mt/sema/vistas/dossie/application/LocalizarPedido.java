package br.gov.mt.sema.vistas.dossie.application;

import java.time.Clock;
import java.time.LocalDateTime;

import br.gov.mt.sema.vistas.acesso.application.UsuarioAtual;
import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.application.port.RepositorioDePedidos;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.shared.application.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;

/**
 * Localiza o pedido para o usuário da requisição. Só quem o criou, além de Gestor e Administrador, o acessa; para os
 * demais ele não existe (404), o que também não revela se o id é válido. Pedido concluído ou à espera de decisão e
 * vencido (sem atividade dentro do TTL) é expirado aqui, com o diretório apagado,
 * sem esperar a limpeza agendada.
 */
@Service
public class LocalizarPedido {

    private final RepositorioDePedidos pedidos;
    private final UsuarioAtual usuarioAtual;
    private final ParametrosDoDossie parametros;
    private final Clock relogio;

    public LocalizarPedido(RepositorioDePedidos pedidos, UsuarioAtual usuarioAtual, ParametrosDoDossie parametros,
            Clock relogio) {
        this.pedidos = pedidos;
        this.usuarioAtual = usuarioAtual;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    public PedidoDeDossie executar(String id) {
        Usuario usuario = usuarioAtual.obter();
        PedidoDeDossie pedido = pedidos.buscar(id == null ? "" : id)
                .filter(p -> p.login().equals(usuario.login()) || !usuario.perfil().restritoAoInteressado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dossiê não encontrado."));
        if (pedido.venceu(LocalDateTime.now(relogio))) {
            if (pedido.expirar()) {
                ArquivosTemporarios.apagarPasta(ArquivosTemporarios.pastaDo(parametros, pedido));
            }
        }
        return pedido;
    }
}
