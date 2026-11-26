package br.gov.mt.sema.rastro.acesso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import br.gov.mt.sema.rastro.shared.application.exception.RastroException;
import org.junit.jupiter.api.Test;

/** Proteção que a API não alcança sozinha: outro Administrador desativando o último Administrador ativo. */
class AdministrarUsuariosTest {

    private static final class Cadastro implements CadastroDeUsuarios {

        private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();

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
    void analista_pode_ser_cadastrado_sem_cpf_e_nao_e_restrito_ao_interessado() {
        Cadastro cadastro = new Cadastro();
        AdministrarUsuarios administrar = new AdministrarUsuarios(cadastro);

        UsuarioAdministrado criado = administrar.incluir("Analista Exemplo 04", "analista.exemplo04", null, "ANALISTA");

        assertThat(criado.perfil()).isEqualTo("ANALISTA");
        assertThat(cadastro.buscarPorLogin("analista.exemplo04").orElseThrow().cpfCnpj()).isNull();
    }

    @Test
    void cpf_do_analista_nao_sai_nem_mascarado_na_lista() {
        Cadastro cadastro = new Cadastro();
        cadastro.salvar(new Usuario("analista.exemplo06", "Analista Exemplo 06", Perfil.ANALISTA, "00000000001", true));
        cadastro.salvar(new Usuario("operador.exemplo", "Operador Exemplo", Perfil.OPERADOR, "00000000001", true));
        AdministrarUsuarios administrar = new AdministrarUsuarios(cadastro);

        assertThat(administrar.listar()).filteredOn(u -> u.login().startsWith("analista"))
                .allSatisfy(u -> assertThat(u.cpfCnpjMascarado()).isNull());
        assertThat(administrar.listar()).filteredOn(u -> u.login().startsWith("operador"))
                .allSatisfy(u -> assertThat(u.cpfCnpjMascarado()).isNotNull());
    }

    @Test
    void analista_com_cpf_invalido_e_recusado_e_perfil_desconhecido_cita_analista() {
        AdministrarUsuarios administrar = new AdministrarUsuarios(new Cadastro());

        assertThatThrownBy(() -> administrar.incluir("Analista Exemplo 05", "analista.exemplo05", "000.000.000-01", "ANALISTA"))
                .hasMessageContaining("inválido");
        assertThatThrownBy(() -> administrar.incluir("Fulano Exemplo", "fulano.exemplo", null, "OUTRO"))
                .hasMessageContaining("ANALISTA");
    }

    @Test
    void usuario_sem_cpf_pode_virar_analista_mas_nao_operador() {
        Cadastro cadastro = new Cadastro();
        cadastro.salvar(new Usuario("sem.cpf", "Sem Cpf", Perfil.OPERADOR_SIGILO, null, true));
        AdministrarUsuarios administrar = new AdministrarUsuarios(cadastro);

        administrar.alterar("sem.cpf", "ANALISTA", null, "outro");
        assertThat(cadastro.buscarPorLogin("sem.cpf").orElseThrow().perfil()).isEqualTo(Perfil.ANALISTA);
        assertThatThrownBy(() -> administrar.alterar("sem.cpf", "OPERADOR", null, "outro"))
                .hasMessageContaining("CPF");
    }

    @Test
    void autor_inativo_nao_desativa_o_ultimo_administrador_ativo() {
        Cadastro cadastro = new Cadastro();
        cadastro.salvar(new Usuario("ativo", "Ativo", Perfil.ADMIN, null, true));
        cadastro.salvar(new Usuario("autor", "Autor", Perfil.ADMIN, null, false));
        AdministrarUsuarios administrar = new AdministrarUsuarios(cadastro);

        assertThatThrownBy(() -> administrar.alterar("ativo", null, false, "autor"))
                .isInstanceOfSatisfying(RastroException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ULTIMO_ADMINISTRADOR));
        assertThat(cadastro.buscarPorLogin("ativo").orElseThrow().ativo()).isTrue();
    }
}
