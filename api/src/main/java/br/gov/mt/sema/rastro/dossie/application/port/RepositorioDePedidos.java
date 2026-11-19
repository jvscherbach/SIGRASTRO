package br.gov.mt.sema.rastro.dossie.application.port;

import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.dossie.domain.PedidoDeDossie;

/** Pedidos em memória no protótipo (D09). */
public interface RepositorioDePedidos {

    void salvar(PedidoDeDossie pedido);

    Optional<PedidoDeDossie> buscar(String id);

    List<PedidoDeDossie> todos();

    void remover(String id);
}
