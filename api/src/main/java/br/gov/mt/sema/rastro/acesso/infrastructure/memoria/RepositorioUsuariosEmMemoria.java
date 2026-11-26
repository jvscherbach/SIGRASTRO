package br.gov.mt.sema.rastro.acesso.infrastructure.memoria;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.rastro.acesso.application.CadastroDeUsuarios;
import br.gov.mt.sema.rastro.acesso.domain.HashDeSenha;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Usuários do protótipo. Cada usuário guarda o hash da própria senha ({@link HashDeSenha}, sal próprio). Os incluídos ou
 * alterados somem ao reiniciar (D09).
 *
 * <p>TRANSITÓRIO (L1, bloco 1): os três usuários de demonstração ainda existem, cada um com o hash da senha de
 * {@code rastro.demo.senha}, e um usuário incluído sem senha recebe esse mesmo hash até o bloco 3 (senha na inclusão).
 * O bloco 2 remove os de demonstração e este atalho.
 */
// S5: persistência — na versão definitiva, tabela de usuários no schema próprio (docs/12), atrás de CadastroDeUsuarios.
@Repository
class RepositorioUsuariosEmMemoria implements CadastroDeUsuarios {

    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();
    private final Map<String, Instant> ultimosAcessos = new ConcurrentHashMap<>();

    private final String senhaDemo;
    private final HashDeSenha hashes;
    private final Clock relogio;

    RepositorioUsuariosEmMemoria(@Value("${rastro.demo.senha:}") String senhaDemo, HashDeSenha hashes, Clock relogio) {
        this.senhaDemo = senhaDemo;
        this.hashes = hashes;
        this.relogio = relogio;
        semear(new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, "12.345.678/0001-90", true));
        semear(new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true));
        semear(new Usuario("admin.demo", "Admin Demo", Perfil.ADMIN, null, true));
    }

    /** Sem {@code rastro.demo.senha} o usuário existe, mas sem senha: não entra (login desabilitado). */
    private void semear(Usuario usuario) {
        usuarios.put(usuario.login(), senhaDemo.isEmpty() ? usuario : usuario.comSenhaHash(hashes.gerar(senhaDemo)));
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
        boolean semSenha = usuario.senhaHash() == null && !senhaDemo.isEmpty();
        usuarios.put(usuario.login(), semSenha ? usuario.comSenhaHash(hashes.gerar(senhaDemo)) : usuario);
    }

    @Override
    public Optional<Instant> ultimoAcesso(String login) {
        return Optional.ofNullable(ultimosAcessos.get(login));
    }
}
