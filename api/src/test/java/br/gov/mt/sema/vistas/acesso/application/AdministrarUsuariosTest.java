package br.gov.mt.sema.vistas.acesso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.vistas.acesso.domain.Perfil;
import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;
import br.gov.mt.sema.vistas.shared.application.exception.VistasException;
import org.junit.jupiter.api.Test;

/** Proteção que a API não alcança sozinha: outro Administrador desativando o último Administrador ativo. */
class AdministrarUsuariosTest {

    private static final class Cadastro implements CadastroDeUsuarios {

        private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();

        @Override
        public Optional<Usuario> autenticar(String login, String senha) {
            return Optional.empty();
        }

        @Override
        public Optional<Usuario> buscarPorLogin(String login) {
            return Optional.ofNullable(usuarios.get(login));
        }

        @Override
        public List<Usuario> listar() {
            return new ArrayList<>(usuarios.values());
        }

        @Override
        public void salvar(Usuario usuario) {
            usuarios.put(usuario.login(), usuario);
        }

        @Override
        public Optional<Instant> ultimoAcesso(String login) {
            return Optional.empty();
        }
    }

    @Test
    void autor_inativo_nao_desativa_o_ultimo_administrador_ativo() {
        Cadastro cadastro = new Cadastro();
        cadastro.salvar(new Usuario("ativo", "Ativo", Perfil.ADMIN, null, true));
        cadastro.salvar(new Usuario("autor", "Autor", Perfil.ADMIN, null, false));
        AdministrarUsuarios administrar = new AdministrarUsuarios(cadastro);

        assertThatThrownBy(() -> administrar.alterar("ativo", null, false, "autor"))
                .isInstanceOfSatisfying(VistasException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ULTIMO_ADMINISTRADOR));
        assertThat(cadastro.buscarPorLogin("ativo").orElseThrow().ativo()).isTrue();
    }
}
