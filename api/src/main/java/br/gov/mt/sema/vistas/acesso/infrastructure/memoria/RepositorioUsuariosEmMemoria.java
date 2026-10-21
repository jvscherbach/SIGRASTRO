package br.gov.mt.sema.vistas.acesso.infrastructure.memoria;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.vistas.acesso.application.RepositorioUsuarios;
import br.gov.mt.sema.vistas.acesso.domain.Perfil;
import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/** Usuários de demonstração do protótipo, todos com a mesma senha (vistas.demo.senha). */
@Repository
class RepositorioUsuariosEmMemoria implements RepositorioUsuarios {

    private static final Map<String, Usuario> USUARIOS = Map.of(
            "operador.demo", new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, "12.345.678/0001-90", true),
            "gestor.demo", new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true),
            "admin.demo", new Usuario("admin.demo", "Admin Demo", Perfil.ADMIN, null, true));

    private final byte[] senhaDemo;

    RepositorioUsuariosEmMemoria(@Value("${vistas.demo.senha:}") String senhaDemo) {
        this.senhaDemo = senhaDemo.getBytes(StandardCharsets.UTF_8);
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
        return Optional.ofNullable(login).map(USUARIOS::get);
    }
}
