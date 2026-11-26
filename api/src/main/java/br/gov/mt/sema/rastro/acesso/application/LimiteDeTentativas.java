package br.gov.mt.sema.rastro.acesso.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import br.gov.mt.sema.rastro.shared.application.exception.LimiteExcedidoException;
import org.springframework.stereotype.Component;

/**
 * Limite de tentativas de login (L1), em memória (zera ao reiniciar; persistência é da H1).
 *
 * <ul>
 *   <li>Por par <b>login + origem</b>: {@code tentativas} senhas erradas dentro da janela bloqueiam o par por
 *       {@code bloqueioMinutos}. O par (e não só o login) evita que quem erra de outro lugar tranque o Administrador.
 *   <li>Por <b>origem</b>: {@code limitePorOrigem} falhas dentro da janela, somando todos os logins, bloqueiam a origem.
 * </ul>
 *
 * Bloqueado, até a senha certa é recusada. Acerto antes do bloqueio zera o contador do par. A contagem não depende de o
 * login existir: a resposta é a mesma para login existente e inexistente. Todos os métodos são sincronizados: o custo é
 * desprezível diante do hash de senha.
 */
@Component
public class LimiteDeTentativas {

    private static final int MAX_CHAVE = 100;
    private static final String ORIGEM_DESCONHECIDA = "?";

    private final Clock relogio;
    private final ParametrosDoLogin parametros;
    private final Map<String, Registro> porPar = new HashMap<>();
    private final Map<String, Registro> porOrigem = new HashMap<>();

    public LimiteDeTentativas(Clock relogio, ParametrosDoLogin parametros) {
        this.relogio = relogio;
        this.parametros = parametros;
    }

    private static final class Registro {
        private final Deque<Instant> falhas = new ArrayDeque<>();
        private Instant bloqueadoAte;
    }

    /** @throws LimiteExcedidoException se o par ou a origem estiver bloqueado */
    public synchronized void exigirLiberado(String login, String origem) {
        Instant agora = relogio.instant();
        long restante = Math.max(restante(porPar.get(par(login, origem)), agora),
                restante(porOrigem.get(origem(origem)), agora));
        if (restante > 0) {
            throw new LimiteExcedidoException(restante);
        }
    }

    /** Conta uma senha errada. @return {@code true} se esta falha iniciou um bloqueio (do par ou da origem) */
    public synchronized boolean registrarFalha(String login, String origem) {
        Instant agora = relogio.instant();
        limpar(agora);
        boolean bloqueouPar = contar(porPar.computeIfAbsent(par(login, origem), k -> new Registro()), agora,
                parametros.tentativas());
        boolean bloqueouOrigem = contar(porOrigem.computeIfAbsent(origem(origem), k -> new Registro()), agora,
                parametros.limitePorOrigem());
        return bloqueouPar || bloqueouOrigem;
    }

    /** Entrada válida: zera o contador do par (o da origem continua). */
    public synchronized void registrarAcerto(String login, String origem) {
        porPar.remove(par(login, origem));
    }

    private boolean contar(Registro registro, Instant agora, int limite) {
        Duration janela = Duration.ofMinutes(parametros.janelaMinutos());
        while (!registro.falhas.isEmpty() && registro.falhas.peekFirst().plus(janela).isBefore(agora)) {
            registro.falhas.removeFirst();
        }
        registro.falhas.addLast(agora);
        if (registro.falhas.size() >= limite) {
            registro.bloqueadoAte = agora.plus(Duration.ofMinutes(parametros.bloqueioMinutos()));
            registro.falhas.clear();
            return true;
        }
        return false;
    }

    private static long restante(Registro registro, Instant agora) {
        if (registro == null || registro.bloqueadoAte == null || !agora.isBefore(registro.bloqueadoAte)) {
            return 0;
        }
        long milissegundos = Duration.between(agora, registro.bloqueadoAte).toMillis();
        return (milissegundos + 999) / 1000;
    }

    /** Descarta o que já não conta: bloqueio vencido e falhas fora da janela. */
    private void limpar(Instant agora) {
        Duration janela = Duration.ofMinutes(parametros.janelaMinutos());
        porPar.values().removeIf(r -> vencido(r, agora, janela));
        porOrigem.values().removeIf(r -> vencido(r, agora, janela));
    }

    private static boolean vencido(Registro registro, Instant agora, Duration janela) {
        if (registro.bloqueadoAte != null) {
            return !agora.isBefore(registro.bloqueadoAte);
        }
        return registro.falhas.isEmpty() || registro.falhas.peekLast().plus(janela).isBefore(agora);
    }

    private static String par(String login, String origem) {
        return origem(origem) + "|" + (login == null ? "" : cortar(login.strip().toLowerCase(Locale.ROOT)));
    }

    private static String origem(String origem) {
        return origem == null || origem.isBlank() ? ORIGEM_DESCONHECIDA : cortar(origem.strip());
    }

    private static String cortar(String texto) {
        return texto.length() > MAX_CHAVE ? texto.substring(0, MAX_CHAVE) : texto;
    }
}
