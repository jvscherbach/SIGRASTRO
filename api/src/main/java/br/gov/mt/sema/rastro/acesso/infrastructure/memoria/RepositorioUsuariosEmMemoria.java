package br.gov.mt.sema.rastro.acesso.infrastructure.memoria;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.rastro.acesso.application.CadastroDeUsuarios;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import org.springframework.stereotype.Repository;

/**
 * Usuários do protótipo. Cada usuário guarda o hash da própria senha ({@code HashDeSenha}, sal próprio). Nasce vazio: os
 * usuários padrão são criados na subida por {@code CriarUsuariosPadrao}, a partir das variáveis de ambiente. Os
 * incluídos ou alterados somem ao reiniciar, e o serviço volta ao que as variáveis dizem (D09).
 */
// S5: persistência — na versão definitiva, tabela de usuários no schema próprio (docs/12), atrás de CadastroDeUsuarios.
@Repository
class RepositorioUsuariosEmMemoria implements CadastroDeUsuarios {

    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();
    private final Map<String, Instant> ultimosAcessos = new ConcurrentHashMap<>();

    private final Clock relogio;

    RepositorioUsuariosEmMemoria(Clock relogio) {
        this.relogio = relogio;
    }

    @Override
    public Optional<Usuario> buscarPorLogin(String login) {
        return Optional.ofNullable(login).map(l -> usuarios.get(l.strip().toLowerCase(Locale.ROOT)));
    }

    @Override
    public void registrarAcesso(String login) {
        ultimosAcessos.put(login, relogio.instant());
    }

    @Override
    public List<Usuario> listar() {
        return List.copyOf(usuarios.values());
    }

    @Override
    public void salvar(Usuario usuario) {
        usuarios.put(usuario.login(), usuario);
    }

    @Override
    public Optional<Instant> ultimoAcesso(String login) {
        return Optional.ofNullable(ultimosAcessos.get(login));
    }
}
