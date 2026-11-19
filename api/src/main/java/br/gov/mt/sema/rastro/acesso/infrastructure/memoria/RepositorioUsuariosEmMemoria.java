package br.gov.mt.sema.rastro.acesso.infrastructure.memoria;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.rastro.acesso.application.CadastroDeUsuarios;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Usuários do protótipo, todos com a mesma senha (rastro.demo.senha). Os incluídos ou alterados somem ao reiniciar: o
 * serviço volta aos três de demonstração.
 */
// S5: persistência — na versão definitiva, tabela de usuários no schema próprio (docs/12), atrás de CadastroDeUsuarios.
@Repository
class RepositorioUsuariosEmMemoria implements CadastroDeUsuarios {

    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>(Map.of(
            "operador.demo", new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, "12.345.678/0001-90", true),
            "gestor.demo", new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true),
            "admin.demo", new Usuario("admin.demo", "Admin Demo", Perfil.ADMIN, null, true)));
    private final Map<String, Instant> ultimosAcessos = new ConcurrentHashMap<>();

    private final byte[] senhaDemo;
    private final Clock relogio;

    RepositorioUsuariosEmMemoria(@Value("${rastro.demo.senha:}") String senhaDemo, Clock relogio) {
        this.senhaDemo = senhaDemo.getBytes(StandardCharsets.UTF_8);
        this.relogio = relogio;
    }

    @Override
    public Optional<Usuario> autenticar(String login, String senha) {
        if (senhaDemo.length == 0 || login == null || senha == null) {
            return Optional.empty();
        }
        boolean confere = MessageDigest.isEqual(senhaDemo, senha.getBytes(StandardCharsets.UTF_8));
        return confere ? buscarPorLogin(login) : Optional.empty();
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
