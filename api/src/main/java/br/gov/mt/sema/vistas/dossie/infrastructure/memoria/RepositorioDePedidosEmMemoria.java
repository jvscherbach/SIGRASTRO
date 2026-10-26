package br.gov.mt.sema.vistas.dossie.infrastructure.memoria;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.vistas.dossie.application.port.RepositorioDePedidos;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import org.springframework.stereotype.Component;

/** Pedidos em memória (D09): somem num reinício, e a pasta temporária é esvaziada na subida. */
@Component
class RepositorioDePedidosEmMemoria implements RepositorioDePedidos {

    private final Map<String, PedidoDeDossie> pedidos = new ConcurrentHashMap<>();

    @Override
    public void salvar(PedidoDeDossie pedido) {
        pedidos.put(pedido.id(), pedido);
    }

    @Override
    public Optional<PedidoDeDossie> buscar(String id) {
        return Optional.ofNullable(pedidos.get(id));
    }

    @Override
    public List<PedidoDeDossie> todos() {
        return List.copyOf(pedidos.values());
    }

    @Override
    public void remover(String id) {
        pedidos.remove(id);
    }
}
