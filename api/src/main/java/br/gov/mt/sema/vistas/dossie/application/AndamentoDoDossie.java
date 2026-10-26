package br.gov.mt.sema.vistas.dossie.application;

import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.vistas.dossie.domain.ErroDoDossie;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.dossie.domain.SituacaoItem;
import br.gov.mt.sema.vistas.dossie.domain.StatusDossie;

/**
 * Retrato do pedido para o acompanhamento (RF16). Não carrega chave de arquivo. {@code falhas} são os itens em FALHA
 * (o que a decisão vai refazer ou remover); {@code erro}, o motivo geral (credencial recusada ou falha na montagem).
 */
public record AndamentoDoDossie(String id, StatusDossie status, int total, int processados, ErroDoDossie erro,
        List<Item> falhas, Integer paginas, LocalDateTime expiraEm, List<Item> itens) {

    public record Item(String origem, String id, String titulo, SituacaoItem situacao, int tentativas, String motivo) {
    }

    static AndamentoDoDossie de(PedidoDeDossie pedido) {
        List<Item> itens = pedido.itens().stream()
                .map(i -> new Item(i.origem(), i.id(), i.titulo(), i.situacao(), i.tentativas(), i.motivo()))
                .toList();
        return new AndamentoDoDossie(pedido.id(), pedido.status(), pedido.itens().size(), pedido.processados(),
                pedido.erro(), itens.stream().filter(i -> i.situacao() == SituacaoItem.FALHA).toList(),
                pedido.paginas(), pedido.expiraEm(), itens);
    }
}
