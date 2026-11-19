package br.gov.mt.sema.rastro.dossie.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.rastro.dossie.domain.ErroDoDossie;
import br.gov.mt.sema.rastro.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.rastro.dossie.domain.SituacaoItem;
import br.gov.mt.sema.rastro.dossie.domain.StatusDossie;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import org.springframework.stereotype.Service;

/**
 * "Dossiês gerados" (S4, bloco 7): os pedidos do histórico em memória que o usuário acessa, pela política de acesso.
 * Operador: só os seus (e só enquanto for o interessado gravado no pedido); Gestor e Administrador: todos, ou só os seus
 * com {@code somenteMeus}. Encerrados ficam até {@code DOSSIE_HISTORICO_HORAS} depois do vencimento, sem arquivo. Nada
 * de documento sai daqui (título, nome de arquivo, chave); quem gerou só para quem vê pedidos de outros. Mais recente
 * primeiro. Some num reinício (D09); o registro permanente é a auditoria (S5).
 */
@Service
public class ListarDossiesGerados {

    static final int TAMANHO_PADRAO = 20;
    static final int TAMANHO_MAXIMO = 50;

    private static final Comparator<PedidoDeDossie> MAIS_RECENTE_PRIMEIRO = Comparator
            .comparing(PedidoDeDossie::criadoEm, Comparator.reverseOrder())
            .thenComparing(PedidoDeDossie::id);

    private final LocalizarPedido localizar;
    private final PoliticaDeAcesso politica;
    private final ParametrosDoDossie parametros;
    private final Clock relogio;

    public ListarDossiesGerados(LocalizarPedido localizar, PoliticaDeAcesso politica, ParametrosDoDossie parametros,
            Clock relogio) {
        this.localizar = localizar;
        this.politica = politica;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    /** Situação na lista: a do pedido, com BAIXADO separado de EXPIRADO (RNF05). */
    public enum Situacao {
        AGUARDANDO,
        OBTENDO,
        PENDENTE_DECISAO,
        MONTANDO,
        CONCLUIDO,
        CANCELADO,
        EXPIRADO,
        BAIXADO,
        /** Sem espaço em disco ou arquivo final perdido (S6, bloco 3): sem arquivo, só gerar de novo. */
        FALHA
    }

    /**
     * @param documentos   documentos do pedido que não foram removidos na decisão
     * @param falhas       documentos em falha agora (à espera de decisão)
     * @param tamanhoBytes só com arquivo para baixar
     * @param arquivoGerado o PDF chegou a ser montado (EXPIRADO sem ele = "nenhum arquivo gerado")
     * @param geradoPor    nome de quem gerou; nulo para o Operador
     * @param baixado      o download já foi completo e o arquivo está na janela de nova tentativa (D77)
     * @param codigoDaFalha código do motivo em FALHA (ex.: ARMAZENAMENTO_INSUFICIENTE); nulo nas outras situações
     * @param motivoDaFalha texto para o usuário em FALHA, sem caminho de arquivo; nulo nas outras situações
     */
    public record DossieGerado(String id, long idRequerimento, String rotulo, String numero, Situacao situacao,
            LocalDateTime criadoEm, LocalDateTime expiraEm, int documentos, int falhas, int listados,
            Long tamanhoBytes, boolean entregaPorTicket, boolean temArquivo, boolean arquivoGerado, String geradoPor,
            boolean baixado, String codigoDaFalha, String motivoDaFalha) {
    }

    public record Pagina(List<DossieGerado> itens, int pagina, int tamanho, long total) {
    }

    public Pagina executar(boolean somenteMeus, Integer pagina, Integer tamanho) {
        int numeroDaPagina = pagina == null ? 0 : pagina;
        int tamanhoDaPagina = tamanho == null ? TAMANHO_PADRAO : tamanho;
        if (numeroDaPagina < 0) {
            throw new ParametroInvalidoException("pagina", "A página começa em 0.");
        }
        if (tamanhoDaPagina < 1 || tamanhoDaPagina > TAMANHO_MAXIMO) {
            throw new ParametroInvalidoException("tamanho", "O tamanho deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        boolean veOutros = politica.podeVerPedidosDeOutros();
        String login = politica.loginDoUsuarioAtual();
        LocalDateTime agora = LocalDateTime.now(relogio);
        List<PedidoDeDossie> visiveis = localizar.visiveis().stream()
                .filter(p -> !(veOutros && somenteMeus) || p.login().equals(login))
                .filter(p -> !(p.status().terminal() && LimparDossies.foraDoHistorico(p, agora, parametros)))
                .sorted(MAIS_RECENTE_PRIMEIRO)
                .toList();
        long limiteDoBlob = parametros.blobMaxMb() * 1024L * 1024L;
        List<DossieGerado> itens = visiveis.stream()
                .skip((long) numeroDaPagina * tamanhoDaPagina)
                .limit(tamanhoDaPagina)
                .map(p -> item(p, veOutros, limiteDoBlob))
                .toList();
        return new Pagina(itens, numeroDaPagina, tamanhoDaPagina, visiveis.size());
    }

    private static DossieGerado item(PedidoDeDossie pedido, boolean veOutros, long limiteDoBlob) {
        StatusDossie status = pedido.status();
        boolean temArquivo = status == StatusDossie.CONCLUIDO && pedido.arquivo() != null;
        Long tamanho = temArquivo ? pedido.tamanhoBytes() : null;
        int documentos = (int) pedido.itens().stream().filter(i -> i.situacao() != SituacaoItem.REMOVIDO).count();
        ErroDoDossie erro = status == StatusDossie.FALHA ? pedido.erro() : null;
        return new DossieGerado(pedido.id(), pedido.idRequerimento(), pedido.capa().rotulo(), pedido.capa().numero(),
                situacao(pedido), pedido.criadoEm(), pedido.expiraEm(), documentos, pedido.falhas().size(),
                pedido.listados(), tamanho, tamanho != null && tamanho > limiteDoBlob, temArquivo,
                pedido.arquivoGerado(), veOutros ? pedido.capa().usuario() : null,
                temArquivo && pedido.baixado(), erro == null ? null : erro.codigo(), erro == null ? null : erro.motivo());
    }

    private static Situacao situacao(PedidoDeDossie pedido) {
        if (pedido.status() == StatusDossie.EXPIRADO && pedido.baixado()) {
            return Situacao.BAIXADO;
        }
        return Situacao.valueOf(pedido.status().name());
    }
}
