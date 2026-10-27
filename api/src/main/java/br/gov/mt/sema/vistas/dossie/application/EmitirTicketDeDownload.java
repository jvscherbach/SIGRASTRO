package br.gov.mt.sema.vistas.dossie.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

import br.gov.mt.sema.vistas.acesso.application.UsuarioAtual;
import br.gov.mt.sema.vistas.dossie.application.port.TicketsDeDownload;
import br.gov.mt.sema.vistas.dossie.domain.TicketDeDownload;
import org.springframework.stereotype.Service;

/**
 * Emite o ticket de uso único para o navegador baixar um dossiê grande em streaming (S2.2, D49): 256 bits aleatórios,
 * válidos por {@value TicketDeDownload#SEGUNDOS_DE_VALIDADE} s, presos ao pedido e ao usuário. Valem as mesmas regras do
 * arquivo (acesso, 409 sem arquivo, 410 expirado), conferidas antes de emitir.
 */
@Service
public class EmitirTicketDeDownload {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final ObterArquivoDoDossie obterArquivo;
    private final TicketsDeDownload tickets;
    private final UsuarioAtual usuarioAtual;
    private final Clock relogio;

    public EmitirTicketDeDownload(ObterArquivoDoDossie obterArquivo, TicketsDeDownload tickets,
            UsuarioAtual usuarioAtual, Clock relogio) {
        this.obterArquivo = obterArquivo;
        this.tickets = tickets;
        this.usuarioAtual = usuarioAtual;
        this.relogio = relogio;
    }

    public TicketEmitido executar(String id) {
        ObterArquivoDoDossie.ArquivoDoDossie arquivo = obterArquivo.executar(id);
        LocalDateTime agora = LocalDateTime.now(relogio);
        tickets.descartarVencidos(agora);
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expiraEm = agora.plusSeconds(TicketDeDownload.SEGUNDOS_DE_VALIDADE);
        tickets.guardar(TicketDeDownload.resumo(valor),
                new TicketDeDownload(arquivo.id(), usuarioAtual.obter().login(), expiraEm));
        return new TicketEmitido(valor, expiraEm);
    }

    /** O valor só existe nesta resposta. */
    public record TicketEmitido(String ticket, LocalDateTime expiraEm) {

        @Override
        public String toString() {
            return "TicketEmitido[expiraEm=" + expiraEm + "]";
        }
    }
}
