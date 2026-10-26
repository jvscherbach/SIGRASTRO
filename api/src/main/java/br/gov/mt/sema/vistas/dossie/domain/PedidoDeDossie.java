package br.gov.mt.sema.vistas.dossie.domain;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pedido de geração, mantido em memória (D09). Criado com o instantâneo dos documentos já resolvido, então a geração
 * não precisa do SIGA. As transições são sincronizadas: o executor, o cancelamento, a decisão do usuário, a leitura do
 * andamento e a limpeza agendada o acessam de threads diferentes.
 *
 * <p>S2.2 (docs/06): AGUARDANDO → OBTENDO → (PENDENTE_DECISAO ⇄ AGUARDANDO) → MONTANDO → CONCLUIDO. Falhas não
 * encerram o pedido: ele espera a decisão do usuário, e a continuação não refaz o que já foi obtido. A validade
 * ({@code expiraEm}) conta desde a última atividade.
 */
public final class PedidoDeDossie {

    /** Resultado de {@link #decidir}. */
    public enum Decisao {
        ACEITA,
        NAO_PENDENTE,
        NADA_A_MONTAR
    }

    private final String id;
    private final String login;
    private final long idRequerimento;
    private final DadosDaCapa capa;
    private final List<ItemDoPedido> itens;
    private final LocalDateTime criadoEm;

    private StatusDossie status = StatusDossie.AGUARDANDO;
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

    /** Itens resolvidos (OK ou REMOVIDO): o progresso nunca volta atrás numa continuação. */
    public synchronized int processados() {
        return (int) itens.stream().filter(i -> i.situacao().resolvido()).count();
    }

    /** Itens em FALHA (o que a decisão vai refazer ou remover). */
    public List<ItemDoPedido> falhas() {
        return itens.stream().filter(i -> i.situacao() == SituacaoItem.FALHA).toList();
    }

    /** Motivo geral (credencial recusada ou falha na montagem); as falhas por documento ficam nos itens. */
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

    /** AGUARDANDO vira OBTENDO; falso se o pedido foi cancelado antes de começar (ou não está na fila). */
    public synchronized boolean iniciar() {
        if (status != StatusDossie.AGUARDANDO || cancelamentoSolicitado) {
            return false;
        }
        status = StatusDossie.OBTENDO;
        return true;
    }

    /** Houve atividade: a validade passa a contar de agora. */
    public synchronized void registrarAtividade(LocalDateTime novaValidade) {
        this.expiraEm = novaValidade;
    }

    /** Terminada a obtenção com falhas (ou credencial recusada, ou falha na montagem): espera a decisão do usuário. */
    public synchronized boolean aguardarDecisao(ErroDoDossie erroGeral, LocalDateTime novaValidade) {
        if (cancelamentoSolicitado || !status.emAndamento()) {
            return false;
        }
        this.status = StatusDossie.PENDENTE_DECISAO;
        this.erro = erroGeral;
        this.expiraEm = novaValidade;
        return true;
    }

    /** Todos os itens resolvidos: começa a montagem. Falso se cancelado. */
    public synchronized boolean iniciarMontagem() {
        if (cancelamentoSolicitado || status != StatusDossie.OBTENDO) {
            return false;
        }
        this.status = StatusDossie.MONTANDO;
        return true;
    }

    /** Falso se o pedido foi cancelado durante a montagem: o chamador descarta o arquivo. */
    public synchronized boolean concluir(Path arquivo, int paginas, long tamanhoBytes, LocalDateTime expiraEm) {
        if (cancelamentoSolicitado || status != StatusDossie.MONTANDO) {
            return false;
        }
        this.status = StatusDossie.CONCLUIDO;
        this.erro = null;
        this.arquivo = arquivo;
        this.paginas = paginas;
        this.tamanhoBytes = tamanhoBytes;
        this.expiraEm = expiraEm;
        return true;
    }

    /**
     * Decisão do usuário (S2.2). TENTAR_NOVAMENTE devolve à fila só os itens não resolvidos; REMOVER_E_CONTINUAR os
     * marca como REMOVIDO e segue para a montagem. Nada já obtido é descartado. O chamador enfileira a geração quando
     * a decisão é aceita.
     */
    public synchronized Decisao decidir(AcaoDeContinuacao acao, LocalDateTime novaValidade) {
        if (status != StatusDossie.PENDENTE_DECISAO || cancelamentoSolicitado) {
            return Decisao.NAO_PENDENTE;
        }
        if (acao == AcaoDeContinuacao.REMOVER_E_CONTINUAR
                && itens.stream().noneMatch(i -> i.situacao() == SituacaoItem.OK)) {
            return Decisao.NADA_A_MONTAR;
        }
        SituacaoItem destino = acao == AcaoDeContinuacao.TENTAR_NOVAMENTE ? SituacaoItem.AGUARDANDO : SituacaoItem.REMOVIDO;
        for (ItemDoPedido item : itens) {
            if (!item.situacao().resolvido()) {
                item.situacao(destino);
            }
        }
        this.erro = null;
        this.status = StatusDossie.AGUARDANDO;
        this.expiraEm = novaValidade;
        return Decisao.ACEITA;
    }

    /**
     * Cancela. Em OBTENDO ou MONTANDO só marca: o executor para no próximo passo e apaga o diretório. Nos demais
     * estados não terminais cancela na hora.
     *
     * @return verdadeiro se o chamador deve apagar o diretório do pedido agora
     */
    public synchronized boolean cancelar() {
        if (status.terminal()) {
            return false;
        }
        cancelamentoSolicitado = true;
        if (status == StatusDossie.OBTENDO || status == StatusDossie.MONTANDO) {
            return false;
        }
        this.status = StatusDossie.CANCELADO;
        this.arquivo = null;
        return true;
    }

    /** O executor reconhece o cancelamento de um pedido em andamento. */
    public synchronized void confirmarCancelamento() {
        if (cancelamentoSolicitado && status.emAndamento()) {
            status = StatusDossie.CANCELADO;
            arquivo = null;
        }
    }

    /**
     * Concluído ou à espera de decisão, e vencido (ou já baixado): vira EXPIRADO.
     *
     * @return verdadeiro se o chamador deve apagar o diretório do pedido
     */
    public synchronized boolean expirar() {
        if (status == StatusDossie.CONCLUIDO || status == StatusDossie.PENDENTE_DECISAO) {
            this.status = StatusDossie.EXPIRADO;
            this.arquivo = null;
            return true;
        }
        return false;
    }

    public synchronized boolean venceu(LocalDateTime agora) {
        return expiraEm != null && !agora.isBefore(expiraEm);
    }
}
