package br.gov.mt.sema.rastro.acesso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.domain.HashDeSenha;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.shared.application.exception.LimiteExcedidoException;
import br.gov.mt.sema.rastro.shared.application.exception.NaoAutenticadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/** Login com hash, mesmo caminho para quem não existe, limite de tentativas e eventos sem senha. */
class AutenticarUsuarioTest {

    private static final String SENHA = "senha-de-teste-123";
    private static final String ORIGEM = "10.0.0.1";

    /** Relógio de teste que anda quando mandado. */
    private static final class RelogioDeTeste extends Clock {
        private Instant agora = Instant.parse("2026-01-01T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    /** Conta as conferências de senha: prova que o caminho percorrido é o mesmo. */
    private static final class HashContador extends HashDeSenha {
        final List<String> hashesConferidos = new ArrayList<>();

        HashContador() {
            super(1000);
        }

        @Override
        public boolean confere(String senha, String guardado) {
            hashesConferidos.add(guardado);
            return super.confere(senha, guardado);
        }
    }

    private static final class Usuarios implements RepositorioUsuarios {
        final Map<String, Usuario> mapa = new HashMap<>();

        @Override
        public Optional<Usuario> buscarPorLogin(String login) {
            return Optional.ofNullable(mapa.get(login));
        }
    }

    private static final class Sessoes implements RepositorioSessoes {
        @Override
        public Sessao abrir(String login) {
            return new Sessao("token-de-teste", login, Instant.parse("2026-01-01T20:00:00Z"));
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
        }
    }

    private static final class Eventos implements ApplicationEventPublisher {
        final List<Object> publicados = new ArrayList<>();

        @Override
        public void publishEvent(ApplicationEvent evento) {
            publicados.add(evento);
        }

        @Override
        public void publishEvent(Object evento) {
            publicados.add(evento);
        }
    }

    private RelogioDeTeste relogio;
    private HashContador hashes;
    private Usuarios usuarios;
    private Eventos eventos;
    private AutenticarUsuario autenticar;

    @BeforeEach
    void preparar() {
        relogio = new RelogioDeTeste();
        hashes = new HashContador();
        usuarios = new Usuarios();
        eventos = new Eventos();
        ParametrosDoLogin parametros = new ParametrosDoLogin() {
            @Override
            public int iteracoes() {
                return 1000;
            }

            @Override
            public int tentativas() {
                return 3;
            }

            @Override
            public int janelaMinutos() {
                return 15;
            }

            @Override
            public int bloqueioMinutos() {
                return 15;
            }

            @Override
            public int limitePorOrigem() {
                return 10;
            }
        };
        Sessoes sessoes = new Sessoes();
        usuarios.mapa.put("operador", novo("operador", true));
        usuarios.mapa.put("admin", novo("admin", true));
        usuarios.mapa.put("desativado", novo("desativado", false));
        usuarios.mapa.put("sem-senha", new Usuario("sem-senha", "Sem Senha", Perfil.OPERADOR, null, true));
        autenticar = new AutenticarUsuario(usuarios, sessoes, new ObterUsuarioAtual(usuarios, sessoes), eventos,
                hashes, new LimiteDeTentativas(relogio, parametros));
        hashes.hashesConferidos.clear();
    }

    private Usuario novo(String login, boolean ativo) {
        return new Usuario(login, login, Perfil.OPERADOR, null, ativo, hashes.gerar(SENHA));
    }

    private void errar(String login, String origem) {
        assertThatThrownBy(() -> autenticar.executar(login, "senha-errada-123", null, origem))
                .isInstanceOf(NaoAutenticadoException.class);
    }

    @Test
    void entra_com_a_senha_certa() {
        SessaoAberta sessao = autenticar.executar("operador", SENHA, null, ORIGEM);

        assertThat(sessao.usuario().login()).isEqualTo("operador");
        assertThat(eventos.publicados).hasSize(1).first().isInstanceOf(EntradaRealizada.class);
    }

    @Test
    void usuario_inexistente_desativado_sem_senha_e_senha_errada_recebem_a_mesma_mensagem() {
        List<String> mensagens = new ArrayList<>();
        for (String login : List.of("inexistente", "desativado", "sem-senha", "operador")) {
            assertThatThrownBy(() -> autenticar.executar(login, "senha-errada-123", null, "10.0." + login.length() + ".1"))
                    .isInstanceOf(NaoAutenticadoException.class)
                    .satisfies(e -> mensagens.add(e.getMessage()));
        }

        assertThat(mensagens).containsOnly("Usuário ou senha inválidos.");
    }

    @Test
    void usuario_inexistente_percorre_o_mesmo_caminho_com_o_hash_falso() {
        errar("inexistente", "10.0.0.2");
        errar("desativado", "10.0.0.3");
        errar("sem-senha", "10.0.0.4");
        errar("operador", "10.0.0.5");

        // uma conferência de hash por tentativa; as três primeiras com o hash falso, a última com o do usuário
        assertThat(hashes.hashesConferidos).hasSize(4);
        assertThat(hashes.hashesConferidos.subList(0, 3)).containsOnly(hashes.hashFalso());
        assertThat(hashes.hashesConferidos.get(3)).isNotEqualTo(hashes.hashFalso());
    }

    @Test
    void desativado_nao_entra_nem_com_a_senha_certa() {
        assertThatThrownBy(() -> autenticar.executar("desativado", SENHA, null, ORIGEM))
                .isInstanceOf(NaoAutenticadoException.class);
    }

    @Test
    void tres_erros_bloqueiam_a_quarta_tentativa_por_quinze_minutos_inclusive_com_a_senha_certa() {
        errar("admin", ORIGEM);
        errar("admin", ORIGEM);
        errar("admin", ORIGEM);

        assertThatThrownBy(() -> autenticar.executar("admin", SENHA, null, ORIGEM))
                .isInstanceOfSatisfying(LimiteExcedidoException.class, e -> {
                    assertThat(e.segundosRestantes()).isEqualTo(900);
                    assertThat(e.getMessage()).isEqualTo("Muitas tentativas. Tente de novo em 15 minutos.");
                });
        relogio.avancar(Duration.ofMinutes(14));
        assertThatThrownBy(() -> autenticar.executar("admin", SENHA, null, ORIGEM))
                .isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void liberado_depois_de_quinze_minutos() {
        errar("admin", ORIGEM);
        errar("admin", ORIGEM);
        errar("admin", ORIGEM);

        relogio.avancar(Duration.ofMinutes(15));

        assertThat(autenticar.executar("admin", SENHA, null, ORIGEM).usuario().login()).isEqualTo("admin");
    }

    @Test
    void acerto_antes_do_bloqueio_zera_o_contador() {
        errar("admin", ORIGEM);
        errar("admin", ORIGEM);
        autenticar.executar("admin", SENHA, null, ORIGEM);

        errar("admin", ORIGEM);
        errar("admin", ORIGEM);

        // 2 erros depois do acerto: ainda não bloqueou
        assertThat(autenticar.executar("admin", SENHA, null, ORIGEM).usuario().login()).isEqualTo("admin");
    }

    @Test
    void falhas_fora_da_janela_nao_contam() {
        errar("admin", ORIGEM);
        errar("admin", ORIGEM);
        relogio.avancar(Duration.ofMinutes(16));
        errar("admin", ORIGEM);
        errar("admin", ORIGEM);

        assertThat(autenticar.executar("admin", SENHA, null, ORIGEM).usuario().login()).isEqualTo("admin");
    }

    @Test
    void outra_origem_nao_e_bloqueada_pelo_mesmo_login() {
        errar("admin", ORIGEM);
        errar("admin", ORIGEM);
        errar("admin", ORIGEM);

        assertThat(autenticar.executar("admin", SENHA, null, "10.0.0.9").usuario().login()).isEqualTo("admin");
    }

    @Test
    void login_inexistente_bloqueia_igual_ao_existente() {
        errar("fantasma", ORIGEM);
        errar("fantasma", ORIGEM);
        errar("fantasma", ORIGEM);

        assertThatThrownBy(() -> autenticar.executar("fantasma", SENHA, null, ORIGEM))
                .isInstanceOfSatisfying(LimiteExcedidoException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Muitas tentativas. Tente de novo em 15 minutos."));
    }

    @Test
    void limite_por_origem_soma_todos_os_logins() {
        for (int i = 0; i < 10; i++) {
            errar("login-" + i, ORIGEM);
        }

        assertThatThrownBy(() -> autenticar.executar("operador", SENHA, null, ORIGEM))
                .isInstanceOf(LimiteExcedidoException.class);
        assertThat(autenticar.executar("operador", SENHA, null, "10.0.0.9").usuario().login()).isEqualTo("operador");
    }

    @Test
    void falha_e_bloqueio_viram_eventos_sem_senha_e_sem_login() {
        errar("admin", ORIGEM);
        errar("admin", ORIGEM);
        errar("fantasma", ORIGEM);
        errar("fantasma", ORIGEM);

        List<LoginRecusado> recusas = eventos.publicados.stream().filter(LoginRecusado.class::isInstance)
                .map(LoginRecusado.class::cast).toList();
        assertThat(recusas).hasSize(4);
        assertThat(recusas.stream().filter(LoginRecusado::iniciouBloqueio)).hasSize(0);
        errar("admin", ORIGEM);
        assertThat(eventos.publicados.stream().filter(LoginRecusado.class::isInstance)
                .map(LoginRecusado.class::cast).filter(LoginRecusado::iniciouBloqueio)).hasSize(1);
        assertThat(eventos.publicados.toString()).doesNotContain(SENHA).doesNotContain("senha-errada").doesNotContain("admin");
    }
}
