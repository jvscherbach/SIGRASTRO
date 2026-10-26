package br.gov.mt.sema.vistas.dossie.domain;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pedido de geração, mantido em memória (D09). Criado com o instantâneo dos documentos já resolvido, então a geração
 * não precisa do SIGA. As transições são sincronizadas: o executor, o cancelamento, a leitura do andamento e a
 * limpeza agendada o acessam de threads diferentes.
 */
public final class PedidoDeDossie {

    private final String id;
    private final String login;
    private final long idRequerimento;
    private final DadosDaCapa capa;
    private final List<ItemDoPedido> itens;
    private final LocalDateTime criadoEm;

    private StatusDossie status = StatusDossie.PENDENTE;
    private int processados;
    private ErroDoDossie erro;
    private Integer paginas;
    private Path arquivo;
    private long tamanhoBytes;
    private LocalDateTime expiraEm;
    private boolean cancelamentoSolicitado;

    public PedidoDeDossie(String id, String login, long idRequerimento, DadosDaCapa capa, List<ItemDoPedido> itens,
            LocalDateTime criadoEm, LocalDateTime expiraEm) {
        this.id = id;
        this.login = login;
        this.idRequerimento = idRequerimento;
        this.capa = capa;
        this.itens = List.copyOf(itens);
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public String id() {
        return id;
    }

    public String login() {
        return login;
    }

    public long idRequerimento() {
        return idRequerimento;
    }

    public DadosDaCapa capa() {
        return capa;
    }

    public List<ItemDoPedido> itens() {
        return itens;
    }

    public LocalDateTime criadoEm() {
        return criadoEm;
    }

    public synchronized StatusDossie status() {
        return status;
    }

    public synchronized int processados() {
        return processados;
    }

    public synchronized ErroDoDossie erro() {
        return erro;
    }

    public synchronized Integer paginas() {
        return paginas;
    }

    public synchronized Path arquivo() {
        return arquivo;
    }

    public synchronized long tamanhoBytes() {
        return tamanhoBytes;
    }

    public synchronized LocalDateTime expiraEm() {
        return expiraEm;
    }

    public synchronized boolean cancelamentoSolicitado() {
        return cancelamentoSolicitado;
    }

    /** PENDENTE vira PROCESSANDO; falso se o pedido já foi cancelado antes de começar. */
    public synchronized boolean iniciar() {
        if (status != StatusDossie.PENDENTE || cancelamentoSolicitado) {
            return false;
        }
        status = StatusDossie.PROCESSANDO;
        return true;
    }

    public synchronized void documentoProcessado() {
        processados++;
    }

    /** Falso se o pedido foi cancelado durante a montagem: o chamador descarta o arquivo. */
    public synchronized boolean concluir(Path arquivo, int paginas, long tamanhoBytes, LocalDateTime expiraEm) {
        if (cancelamentoSolicitado) {
            return false;
        }
        this.status = StatusDossie.CONCLUIDO;
        this.arquivo = arquivo;
        this.paginas = paginas;
        this.tamanhoBytes = tamanhoBytes;
        this.expiraEm = expiraEm;
        return true;
    }

    public synchronized void falhar(ErroDoDossie erro) {
        if (status.encerrado()) {
            return;
        }
        this.status = StatusDossie.FALHA;
        this.erro = erro;
    }

    /**
     * Pedido em andamento: marca o cancelamento e o executor para no próximo documento. Concluído: descarta o arquivo.
     * Devolve o arquivo a apagar (se houver).
     */
    public synchronized Path cancelar() {
        if (status == StatusDossie.PENDENTE || status == StatusDossie.PROCESSANDO) {
            cancelamentoSolicitado = true;
            if (status == StatusDossie.PENDENTE) {
                status = StatusDossie.CANCELADO;
            }
            return null;
        }
        if (status == StatusDossie.CONCLUIDO) {
            return descartarArquivo(StatusDossie.CANCELADO);
        }
        return null;
    }

    /** O executor reconhece o cancelamento de um pedido em andamento. */
    public synchronized void confirmarCancelamento() {
        if (cancelamentoSolicitado && status == StatusDossie.PROCESSANDO) {
            status = StatusDossie.CANCELADO;
        }
    }

    /** Concluído e vencido (ou já baixado): vira EXPIRADO e devolve o arquivo a apagar. */
    public synchronized Path expirar() {
        if (status == StatusDossie.CONCLUIDO) {
            return descartarArquivo(StatusDossie.EXPIRADO);
        }
        return null;
    }

    public synchronized boolean venceu(LocalDateTime agora) {
        return expiraEm != null && !agora.isBefore(expiraEm);
    }

    private Path descartarArquivo(StatusDossie novoStatus) {
        Path antigo = arquivo;
        this.arquivo = null;
        this.status = novoStatus;
        return antigo;
    }
}
