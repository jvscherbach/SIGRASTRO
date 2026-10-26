package br.gov.mt.sema.vistas.dossie.domain;

import java.time.LocalDateTime;

/**
 * Documento do pedido, congelado no instante da criação (a lista não é relida durante a geração). A chave do arquivo
 * é interna: só a obtenção do binário a usa, e ela fica fora do {@code toString}.
 */
public final class ItemDoPedido {

    private final String origem;
    private final String origemDescricao;
    private final String id;
    private final String titulo;
    private final LocalDateTime data;
    private final String chave;
    private volatile SituacaoItem situacao = SituacaoItem.AGUARDANDO;
    private volatile int paginas;
    private volatile int tentativas;

    public ItemDoPedido(String origem, String origemDescricao, String id, String titulo, LocalDateTime data,
            String chave) {
        this.origem = origem;
        this.origemDescricao = origemDescricao;
        this.id = id;
        this.titulo = titulo == null || titulo.isBlank() ? "Documento " + id : titulo;
        this.data = data;
        this.chave = chave;
    }

    public String origem() {
        return origem;
    }

    public String origemDescricao() {
        return origemDescricao;
    }

    public String id() {
        return id;
    }

    public String titulo() {
        return titulo;
    }

    public LocalDateTime data() {
        return data;
    }

    /** Interna: nunca vai para DTO, log nem resposta (RNF09). */
    public String chaveDoArquivo() {
        return chave;
    }

    public SituacaoItem situacao() {
        return situacao;
    }

    public void situacao(SituacaoItem nova) {
        this.situacao = nova;
    }

    public int paginas() {
        return paginas;
    }

    public void paginas(int paginas) {
        this.paginas = paginas;
    }

    /** Pedidos feitos ao repositório de arquivos para este item (0 = nenhum; mais de 1 = houve nova tentativa). */
    public int tentativas() {
        return tentativas;
    }

    public void tentativas(int tentativas) {
        this.tentativas = tentativas;
    }

    @Override
    public String toString() {
        return "Item[" + origem + "/" + id + "]";
    }
}
