package br.gov.mt.sema.vistas.acesso.infrastructure.memoria;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.vistas.acesso.application.RepositorioSessoes;
import br.gov.mt.sema.vistas.acesso.application.Sessao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioSessoesEmMemoria implements RepositorioSessoes {

    private static final int BYTES_DO_TOKEN = 32;

    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
    private final SecureRandom aleatorio = new SecureRandom();
    private final Clock relogio;
    private final Duration duracao;

    RepositorioSessoesEmMemoria(Clock relogio, @Value("${vistas.sessao.duracao:PT8H}") Duration duracao) {
        this.relogio = relogio;
        this.duracao = duracao;
    }

    @Override
    public Sessao abrir(String login) {
        removerExpiradas();
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Sessao sessao = new Sessao(token, login, relogio.instant().plus(duracao));
        sessoes.put(token, sessao);
        return sessao;
    }

    @Override
    public Optional<Sessao> buscar(String token) {
        Sessao sessao = token == null ? null : sessoes.get(token);
        if (sessao == null) {
            return Optional.empty();
        }
        if (!sessao.expiraEm().isAfter(relogio.instant())) {
            sessoes.remove(token);
            return Optional.empty();
        }
        return Optional.of(sessao);
    }

    @Override
    public void encerrar(String token) {
        if (token != null) {
            sessoes.remove(token);
        }
    }

    private void removerExpiradas() {
        Instant agora = relogio.instant();
        sessoes.values().removeIf(s -> !s.expiraEm().isAfter(agora));
    }
}
