package br.gov.mt.sema.vistas.dossie.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Ticket de uso único para baixar um dossiê grande direto pelo navegador (S2.2, D49): preso ao pedido e ao usuário
 * que o pediu, válido por pouco tempo. Só o resumo SHA-256 do valor é guardado; o valor sai uma única vez, na resposta
 * do {@code POST}, e nunca vai para log. Não é o token de sessão.
 */
public record TicketDeDownload(String idPedido, String login, LocalDateTime expiraEm) {

    public static final int SEGUNDOS_DE_VALIDADE = 60;

    public boolean venceu(LocalDateTime agora) {
        return !agora.isBefore(expiraEm);
    }

    /** Resumo SHA-256 (hex) do valor do ticket: é a chave de busca, para o valor não ficar na memória do servidor. */
    public static String resumo(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
