package br.gov.mt.sema.rastro.acesso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.rastro.acesso.domain.HashDeSenha;
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

    private static final String SENHA = "senha-de-teste-123";
    private static final String OUTRA = "outra-senha-de-teste-456";

    private final HashDeSenha hashes = new HashDeSenha(1000);
    private final Sessoes sessoes = new Sessoes();

    private static final class Sessoes implements RepositorioSessoes {
        final List<String> encerradosDe = new ArrayList<>();

        @Override
        public Sessao abrir(String login) {
            return null;
        }

        @Override
        public Optional<Sessao> buscar(String token) {
            return Optional.empty();
        }

        @Override
        public void encerrar(String token) {
        }

        @Override
        public void encerrarDoUsuario(String login, String tokenAManter) {
            encerradosDe.add(login + "|" + tokenAManter);
        }
    }

    private AdministrarUsuarios novo(CadastroDeUsuarios cadastro) {
        return new AdministrarUsuarios(cadastro, hashes, sessoes);
    }

    @Test
    void inclusao_exige_senha_pela_politica_e_guarda_so_o_hash() {
        Cadastro cadastro = new Cadastro();
        AdministrarUsuarios administrar = novo(cadastro);

        assertThatThrownBy(() -> administrar.incluir("Gestor Exemplo", "gestor.exemplo", null, "OPERADOR_SIGILO", "curta"))
                .hasMessageContaining("12");
        assertThatThrownBy(() -> administrar.incluir("Gestor Exemplo", "gestor.exemplo", null, "OPERADOR_SIGILO", null))
                .hasMessageContaining("Informe a senha");
        assertThat(cadastro.buscarPorLogin("gestor.exemplo")).isEmpty();

        administrar.incluir("Gestor Exemplo", "gestor.exemplo", null, "OPERADOR_SIGILO", SENHA);

        String hash = cadastro.buscarPorLogin("gestor.exemplo").orElseThrow().senhaHash();
        assertThat(hash).startsWith("pbkdf2-sha256$").doesNotContain(SENHA);
        assertThat(hashes.confere(SENHA, hash)).isTrue();
    }

    @Test
    void redefinicao_troca_o_hash_encerra_todas_as_sessoes_e_nao_devolve_senha() {
        Cadastro cadastro = new Cadastro();
        cadastro.salvar(new Usuario("alvo", "Alvo", Perfil.ANALISTA, null, true, hashes.gerar(SENHA)));
        AdministrarUsuarios administrar = novo(cadastro);

        AdministrarUsuarios.Alteracao alteracao = administrar.alterar("alvo", null, null, OUTRA, "autor");

        assertThat(alteracao.senhaRedefinida()).isTrue();
        assertThat(hashes.confere(OUTRA, cadastro.buscarPorLogin("alvo").orElseThrow().senhaHash())).isTrue();
        assertThat(hashes.confere(SENHA, cadastro.buscarPorLogin("alvo").orElseThrow().senhaHash())).isFalse();
        assertThat(sessoes.encerradosDe).containsExactly("alvo|null");
        assertThat(alteracao.depois().toString()).doesNotContain(OUTRA).doesNotContain("pbkdf2");
    }

    @Test
    void redefinicao_com_senha_fraca_ou_igual_ao_login_nao_altera_nada() {
        Cadastro cadastro = new Cadastro();
        String hashAntigo = hashes.gerar(SENHA);
        cadastro.salvar(new Usuario("alvo.exemplo", "Alvo", Perfil.ANALISTA, null, true, hashAntigo));
        AdministrarUsuarios administrar = novo(cadastro);

        assertThatThrownBy(() -> administrar.alterar("alvo.exemplo", "ADMIN", null, "curta", "autor"))
                .hasMessageContaining("12");
        assertThatThrownBy(() -> administrar.alterar("alvo.exemplo", null, null, "alvo.exemplo", "autor"))
                .hasMessageContaining("12");

        Usuario depois = cadastro.buscarPorLogin("alvo.exemplo").orElseThrow();
        assertThat(depois.senhaHash()).isEqualTo(hashAntigo);
        assertThat(depois.perfil()).isEqualTo(Perfil.ANALISTA);
        assertThat(sessoes.encerradosDe).isEmpty();
    }

    @Test
    void alteracao_sem_senha_mantem_hash_e_sessoes() {
        Cadastro cadastro = new Cadastro();
        String hash = hashes.gerar(SENHA);
        cadastro.salvar(new Usuario("alvo", "Alvo", Perfil.ANALISTA, null, true, hash));
        cadastro.salvar(new Usuario("adm", "Adm", Perfil.ADMIN, null, true, hash));
        AdministrarUsuarios administrar = novo(cadastro);

        AdministrarUsuarios.Alteracao alteracao = administrar.alterar("alvo", null, false, "", "adm");

        assertThat(alteracao.senhaRedefinida()).isFalse();
        assertThat(cadastro.buscarPorLogin("alvo").orElseThrow().senhaHash()).isEqualTo(hash);
        assertThat(sessoes.encerradosDe).isEmpty();
    }

    @Test
    void redefinir_a_senha_nao_burla_a_protecao_do_ultimo_administrador() {
        Cadastro cadastro = new Cadastro();
        cadastro.salvar(new Usuario("unico", "Unico", Perfil.ADMIN, null, true, hashes.gerar(SENHA)));
        AdministrarUsuarios administrar = novo(cadastro);

        assertThatThrownBy(() -> administrar.alterar("unico", "ANALISTA", null, OUTRA, "outro"))
                .isInstanceOfSatisfying(RastroException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ULTIMO_ADMINISTRADOR));
        assertThat(hashes.confere(SENHA, cadastro.buscarPorLogin("unico").orElseThrow().senhaHash())).isTrue();
        assertThat(sessoes.encerradosDe).isEmpty();
    }

    @Test
    void analista_pode_ser_cadastrado_sem_cpf_e_nao_e_restrito_ao_interessado() {
        Cadastro cadastro = new Cadastro();
        AdministrarUsuarios administrar = novo(cadastro);

        UsuarioAdministrado criado = administrar.incluir("Analista Exemplo 04", "analista.exemplo04", null, "ANALISTA", SENHA);

        assertThat(criado.perfil()).isEqualTo("ANALISTA");
        assertThat(cadastro.buscarPorLogin("analista.exemplo04").orElseThrow().cpfCnpj()).isNull();
    }

    @Test
    void cpf_do_analista_nao_sai_nem_mascarado_na_lista() {
        Cadastro cadastro = new Cadastro();
        cadastro.salvar(new Usuario("analista.exemplo06", "Analista Exemplo 06", Perfil.ANALISTA, "00000000001", true));
        cadastro.salvar(new Usuario("operador.exemplo", "Operador Exemplo", Perfil.OPERADOR, "00000000001", true));
        AdministrarUsuarios administrar = novo(cadastro);

        assertThat(administrar.listar()).filteredOn(u -> u.login().startsWith("analista"))
                .allSatisfy(u -> assertThat(u.cpfCnpjMascarado()).isNull());
        assertThat(administrar.listar()).filteredOn(u -> u.login().startsWith("operador"))
                .allSatisfy(u -> assertThat(u.cpfCnpjMascarado()).isNotNull());
    }

    @Test
    void analista_com_cpf_invalido_e_recusado_e_perfil_desconhecido_cita_analista() {
        AdministrarUsuarios administrar = novo(new Cadastro());

        assertThatThrownBy(() -> administrar.incluir("Analista Exemplo 05", "analista.exemplo05", "000.000.000-01", "ANALISTA", SENHA))
                .hasMessageContaining("inválido");
        assertThatThrownBy(() -> administrar.incluir("Fulano Exemplo", "fulano.exemplo", null, "OUTRO", SENHA))
                .hasMessageContaining("ANALISTA");
    }

    @Test
    void usuario_sem_cpf_pode_virar_analista_mas_nao_operador() {
        Cadastro cadastro = new Cadastro();
        cadastro.salvar(new Usuario("sem.cpf", "Sem Cpf", Perfil.OPERADOR_SIGILO, null, true));
        AdministrarUsuarios administrar = novo(cadastro);

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
        AdministrarUsuarios administrar = novo(cadastro);

        assertThatThrownBy(() -> administrar.alterar("ativo", null, false, "autor"))
                .isInstanceOfSatisfying(RastroException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ULTIMO_ADMINISTRADOR));
        assertThat(cadastro.buscarPorLogin("ativo").orElseThrow().ativo()).isTrue();
    }
}
