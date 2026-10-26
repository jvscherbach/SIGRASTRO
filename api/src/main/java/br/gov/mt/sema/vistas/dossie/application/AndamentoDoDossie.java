package br.gov.mt.sema.vistas.dossie.application;

import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.vistas.dossie.domain.ErroDoDossie;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.dossie.domain.SituacaoItem;
import br.gov.mt.sema.vistas.dossie.domain.StatusDossie;

/** Retrato do pedido para o acompanhamento (RF16). Não carrega chave de arquivo. */
public record AndamentoDoDossie(String id, StatusDossie status, int total, int processados, ErroDoDossie erro,
        Integer paginas, LocalDateTime expiraEm, List<Item> itens) {

    public record Item(String origem, String id, String titulo, SituacaoItem situacao, int tentativas) {
    }

    static AndamentoDoDossie de(PedidoDeDossie pedido) {
        return new AndamentoDoDossie(pedido.id(), pedido.status(), pedido.itens().size(), pedido.processados(),
                pedido.erro(), pedido.paginas(), pedido.expiraEm(),
                pedido.itens().stream().map(i -> new Item(i.origem(), i.id(), i.titulo(), i.situacao(), i.tentativas())).toList());
    }
}
