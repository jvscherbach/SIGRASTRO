package br.gov.mt.sema.rastro.dossie.application;

import java.util.ArrayList;
import java.util.List;

import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.DocumentoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.UsuarioDoEvento;
import br.gov.mt.sema.rastro.dossie.domain.AutoriaDoPedido;
import br.gov.mt.sema.rastro.dossie.domain.ItemDoPedido;
import br.gov.mt.sema.rastro.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.rastro.dossie.domain.SituacaoItem;
import org.springframework.stereotype.Component;

/**
 * Eventos de auditoria do dossiê. {@code DOSSIE} e {@code FALHA} vêm da thread de geração e usam a autoria guardada no
 * pedido; {@code DOWNLOAD} é em nome de quem baixou, que pode não ser quem pediu. Nunca leva a chave do arquivo.
 */
@Component
public class EventosDoDossie {

    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;

    public EventosDoDossie(RegistrarEvento registrar, ContextoDoEvento contexto) {
        this.registrar = registrar;
        this.contexto = contexto;
    }

    /** O retrato para guardar no pedido. */
    AutoriaDoPedido autoria(String numeroProcesso, String numeroRequerimento) {
        return new AutoriaDoPedido(contexto.usuario(), numeroProcesso, numeroRequerimento, contexto.origem(),
                contexto.correlationId());
    }

    UsuarioDoEvento usuarioAtual() {
        return contexto.usuario();
    }

    void dossieGerado(PedidoDeDossie pedido) {
        List<ItemDoPedido> incluidos = incluidos(pedido);
        List<DocumentoDoEvento> documentos = new ArrayList<>();
        for (int i = 0; i < incluidos.size(); i++) {
            documentos.add(documento(incluidos.get(i), i + 1));
        }
        long sigilosos = incluidos.stream().filter(ItemDoPedido::sigiloso).count();
        registrar.registrar(daGeracao(TipoDeEvento.DOSSIE, pedido)
                .arquivo(pedido.capa().nomeDoArquivo())
                .documentos(documentos)
                .detalhe(incluidos.size() + " documento(s), " + sigilosos + " sigiloso(s)")
                .criar());
    }

    void falhaNoDocumento(PedidoDeDossie pedido, ItemDoPedido item, String motivo) {
        registrar.registrar(daGeracao(TipoDeEvento.FALHA, pedido)
                .documentos(List.of(documento(item, null)))
                .detalhe(motivo)
                .criar());
    }

    /** Falha que não é de um documento: montagem ou preparo da pasta. */
    void falhaGeral(PedidoDeDossie pedido, String motivo) {
        registrar.registrar(daGeracao(TipoDeEvento.FALHA, pedido).detalhe(motivo).criar());
    }

    void download(PedidoDeDossie pedido, UsuarioDoEvento baixadoPor) {
        UsuarioDoEvento usuario = baixadoPor != null ? baixadoPor : contexto.usuario();
        AutoriaDoPedido autoria = pedido.autoria();
        registrar.registrar(contexto.novo(TipoDeEvento.DOWNLOAD, usuario)
                .requerimento(pedido.idRequerimento(), autoria.numeroProcesso(), autoria.numeroRequerimento())
                .interessado(pedido.capa().cpfCnpj())
                .pedido(pedido.id())
                .arquivo(pedido.capa().nomeDoArquivo())
                .sigiloso(incluidos(pedido).stream().anyMatch(ItemDoPedido::sigiloso))
                .criar());
    }

    private static NovoEvento.Construtor daGeracao(TipoDeEvento tipo, PedidoDeDossie pedido) {
        AutoriaDoPedido autoria = pedido.autoria();
        return NovoEvento.de(tipo, autoria.usuario())
                .requerimento(pedido.idRequerimento(), autoria.numeroProcesso(), autoria.numeroRequerimento())
                .interessado(pedido.capa().cpfCnpj())
                .pedido(pedido.id())
                .origem(autoria.origem())
                .correlacao(autoria.correlationId());
    }

    private static List<ItemDoPedido> incluidos(PedidoDeDossie pedido) {
        return pedido.itens().stream().filter(item -> item.situacao() == SituacaoItem.OK).toList();
    }

    private static DocumentoDoEvento documento(ItemDoPedido item, Integer ordem) {
        return new DocumentoDoEvento(item.origemDescricao(), item.id(), item.titulo(), item.nomeArquivo(),
                item.sigiloso(), ordem);
    }
}
