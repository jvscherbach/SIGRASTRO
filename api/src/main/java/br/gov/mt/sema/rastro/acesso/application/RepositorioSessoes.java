package br.gov.mt.sema.rastro.acesso.application;

import java.util.Optional;

public interface RepositorioSessoes {

    Sessao abrir(String login);

    /** Ignora sessões expiradas. */
    Optional<Sessao> buscar(String token);

    void encerrar(String token);

    /** Encerra as sessões abertas do usuário, menos a do token informado ({@code null} = todas). */
    void encerrarDoUsuario(String login, String tokenAManter);
}
