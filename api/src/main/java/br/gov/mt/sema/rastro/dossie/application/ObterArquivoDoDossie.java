package br.gov.mt.sema.rastro.dossie.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;

import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.auditoria.domain.UsuarioDoEvento;
import br.gov.mt.sema.rastro.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.rastro.dossie.application.port.TicketsDeDownload;
import br.gov.mt.sema.rastro.dossie.domain.ErroDoDossie;
import br.gov.mt.sema.rastro.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.rastro.dossie.domain.StatusDossie;
import br.gov.mt.sema.rastro.dossie.domain.TicketDeDownload;
import br.gov.mt.sema.rastro.shared.application.exception.ArquivoDoDossieIndisponivelException;
import br.gov.mt.sema.rastro.shared.application.exception.DossieExpiradoException;
import br.gov.mt.sema.rastro.shared.application.exception.DossieSemArquivoException;
import br.gov.mt.sema.rastro.shared.application.exception.TicketInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Entrega o PDF final. 409 se não há arquivo (em andamento, à espera de decisão, cancelado ou em FALHA), 410 se
 * expirou ou já foi baixado.
 *
 * <p>Arquivo conferido antes de cada entrega (S6, bloco 3): concluído mas sem o arquivo no disco dá 410
 * {@code DOSSIE_ARQUIVO_AUSENTE}; vazio ou com tamanho diferente do gravado dá 500 {@code DOSSIE_ARQUIVO_INVALIDO}. Nos
 * dois casos o pedido vai para FALHA, o diretório é apagado e sai um evento de falha com o código, sem caminho. Nunca
 * 200 com corpo vazio ou truncado.
 *
 * <p>O arquivo é apagado quando o download (attachment) termina por inteiro do lado do servidor (RNF05). O servidor
 * não sabe se o navegador conseguiu gravar: por decisão do responsável (D77) não há nova tentativa, o caminho é gerar
 * de novo.
 *
 * <p>Dossiê grande (S2.2, D49): o navegador baixa direto, sem cabeçalho de sessão, com um ticket de uso único emitido
 * por {@link EmitirTicketDeDownload}. O ticket é consumido na primeira tentativa, válida ou não.
 */
@Service
public class ObterArquivoDoDossie {

    private static final Logger log = LoggerFactory.getLogger(ObterArquivoDoDossie.class);

    private final LocalizarPedido localizar;
    private final ParametrosDoDossie parametros;
    private final TicketsDeDownload tickets;
    private final UsuarioAtual usuarioAtual;
    private final Clock relogio;
    private final EventosDoDossie eventos;

    public ObterArquivoDoDossie(LocalizarPedido localizar, ParametrosDoDossie parametros, TicketsDeDownload tickets,
            UsuarioAtual usuarioAtual, Clock relogio, EventosDoDossie eventos) {
        this.localizar = localizar;
        this.parametros = parametros;
        this.tickets = tickets;
        this.usuarioAtual = usuarioAtual;
        this.relogio = relogio;
        this.eventos = eventos;
    }

    public ArquivoDoDossie executar(String id) {
        return arquivoDe(localizar.executar(id), eventos.usuarioAtual());
    }

    /**
     * Pelo ticket: inexistente, já usado, vencido, de outro pedido, apresentado por outro usuário autenticado ou de quem
     * não acessa mais o pedido (desativado ou rebaixado depois da emissão) dá 403 {@code TICKET_INVALIDO}, sem dizer qual.
     */
    public ArquivoDoDossie executarPorTicket(String id, String ticket) {
        TicketDeDownload valido = tickets.consumir(ticket == null || ticket.isBlank() ? null : TicketDeDownload.resumo(ticket))
                .orElseThrow(TicketInvalidoException::new);
        if (!valido.idPedido().equals(id) || valido.venceu(LocalDateTime.now(relogio))) {
            throw new TicketInvalidoException();
        }
        if (usuarioAtual.talvez().filter(u -> !u.login().equals(valido.login())).isPresent()) {
            throw new TicketInvalidoException();
        }
        PedidoDeDossie pedido = localizar.semUsuario(id);
        if (!localizar.titularAcessa(pedido, valido.login())) {
            throw new TicketInvalidoException();
        }
        return arquivoDe(pedido, valido.usuario());
    }

    /**
     * Chamado quando o download terminou por inteiro: o arquivo (e o diretório do pedido) não precisa mais existir. Só
     * aqui sai o evento {@code DOWNLOAD}: pré-visualização e download interrompido não passam por este método.
     */
    public void concluirDownload(ArquivoDoDossie arquivo) {
        PedidoDeDossie pedido = localizar.semUsuario(arquivo.id());
        eventos.download(pedido, arquivo.baixadoPor());
        pedido.marcarBaixado();
        if (pedido.expirar()) {
            ArquivosTemporarios.apagarPasta(ArquivosTemporarios.pastaDo(parametros, pedido));
        }
    }

    public void concluirDownload(String id) {
        concluirDownload(executar(id));
    }

    private ArquivoDoDossie arquivoDe(PedidoDeDossie pedido, UsuarioDoEvento baixadoPor) {
        StatusDossie status = pedido.status();
        if (status == StatusDossie.EXPIRADO) {
            throw new DossieExpiradoException();
        }
        Path arquivo = pedido.arquivo();
        if (status != StatusDossie.CONCLUIDO || arquivo == null) {
            throw new DossieSemArquivoException(mensagem(status));
        }
        conferirArquivo(pedido, arquivo);
        return new ArquivoDoDossie(pedido.id(), arquivo, pedido.capa().nomeDoArquivo(), pedido.tamanhoBytes(), baixadoPor);
    }

    /** O arquivo final ainda está inteiro no disco; senão o pedido vira FALHA e a resposta é o erro real. */
    private void conferirArquivo(PedidoDeDossie pedido, Path arquivo) {
        long tamanho;
        try {
            tamanho = Files.isRegularFile(arquivo) ? Files.size(arquivo) : -1;
        } catch (IOException e) {
            tamanho = -1;
        }
        if (tamanho < 0) {
            perder(pedido, ErroDoDossie.ARQUIVO_AUSENTE, ArquivoDoDossieIndisponivelException.MENSAGEM_AUSENTE);
            throw ArquivoDoDossieIndisponivelException.ausente();
        }
        if (tamanho == 0 || tamanho != pedido.tamanhoBytes()) {
            perder(pedido, ErroDoDossie.ARQUIVO_INVALIDO, ArquivoDoDossieIndisponivelException.MENSAGEM_INVALIDO);
            throw ArquivoDoDossieIndisponivelException.invalido();
        }
    }

    private void perder(PedidoDeDossie pedido, String codigo, String mensagem) {
        if (pedido.perderArquivo(ErroDoDossie.geral(mensagem, codigo))) {
            ArquivosTemporarios.apagarPasta(ArquivosTemporarios.pastaDo(parametros, pedido));
            eventos.falhaGeral(pedido, codigo + ": o arquivo final do dossiê não pôde ser entregue");
            log.warn("Dossiê {}: arquivo final indisponível na entrega ({}); pedido em FALHA", pedido.id(), codigo);
        }
    }

    private static String mensagem(StatusDossie status) {
        return switch (status) {
            case AGUARDANDO, OBTENDO, MONTANDO -> "O dossiê ainda está sendo gerado.";
            case PENDENTE_DECISAO -> "Há documentos com problema: decida se tenta de novo ou remove antes de montar.";
            case FALHA -> "A geração do dossiê falhou e não há arquivo. Gere o dossiê novamente.";
            default -> "Não há arquivo para este dossiê.";
        };
    }

    /** @param baixadoPor retrato de quem baixa (usuário da sessão ou de quem pediu o ticket) */
    public record ArquivoDoDossie(String id, Path caminho, String nome, long tamanhoBytes, UsuarioDoEvento baixadoPor) {

        public ArquivoDoDossie(String id, Path caminho, String nome, long tamanhoBytes) {
            this(id, caminho, nome, tamanhoBytes, null);
        }
    }
}
