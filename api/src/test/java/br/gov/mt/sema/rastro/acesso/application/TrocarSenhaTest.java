package br.gov.mt.sema.rastro.acesso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.domain.HashDeSenha;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.shared.application.exception.LimiteExcedidoException;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/** Troca da própria senha: senha atual sob limite, política, sessões e auditoria sem senha. */
class TrocarSenhaTest {

    private static final String ATUAL = "senha-de-teste-123";
    private static final String NOVA = "nova-senha-de-teste-456";
    private static final String ORIGEM = "10.0.0.1";

    private final HashDeSenha hashes = new HashDeSenha(1000);
    private final Map<String, Usuario> mapa = new HashMap<>();
    private final List<String> encerrados = new ArrayList<>();
    private final List<Object> publicados = new ArrayList<>();
    private TrocarSenha trocar;

    @BeforeEach
    void preparar() {
        mapa.put("operador", new Usuario("operador", "Operador", Perfil.OPERADOR, "11222333000181", true,
                hashes.gerar(ATUAL)));
        CadastroDeUsuarios cadastro = new CadastroDeUsuarios() {
            @Override
            public List<Usuario> listar() {
                return List.copyOf(mapa.values());
            }

            @Override
            public void salvar(Usuario usuario) {
                mapa.put(usuario.login(), usuario);
            }

            @Override
            public Optional<Instant> ultimoAcesso(String login) {
                return Optional.empty();
            }

            @Override
            public Optional<Usuario> buscarPorLogin(String login) {
                return Optional.ofNullable(mapa.get(login));
            }
        };
        RepositorioSessoes sessoes = new RepositorioSessoes() {
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
                encerrados.add(login + "|" + tokenAManter);
            }
        };
        ApplicationEventPublisher eventos = new ApplicationEventPublisher() {
            @Override
            public void publishEvent(ApplicationEvent evento) {
                publicados.add(evento);
            }

            @Override
            public void publishEvent(Object evento) {
                publicados.add(evento);
            }
        };
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
        trocar = new TrocarSenha(cadastro, hashes, new LimiteDeTentativas(Clock.systemUTC(), parametros), sessoes,
                eventos);
    }

    @Test
    void troca_grava_hash_novo_mantem_a_sessao_atual_e_publica_evento_sem_senha() {
        trocar.executar("operador", "token-atual", ATUAL, NOVA, NOVA, ORIGEM);

        String hash = mapa.get("operador").senhaHash();
        assertThat(hashes.confere(NOVA, hash)).isTrue();
        assertThat(hashes.confere(ATUAL, hash)).isFalse();
        assertThat(encerrados).containsExactly("operador|token-atual");
        assertThat(publicados).singleElement().isInstanceOf(SenhaTrocada.class);
        assertThat(publicados.get(0).toString()).doesNotContain(NOVA).doesNotContain(ATUAL).doesNotContain("pbkdf2");
    }

    @Test
    void senha_atual_errada_e_recusada_sem_alterar_nada() {
        String hashAntes = mapa.get("operador").senhaHash();

        assertThatThrownBy(() -> trocar.executar("operador", "t", "errada-errada-123", NOVA, NOVA, ORIGEM))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("senha atual")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("errada-errada-123"));

        assertThat(mapa.get("operador").senhaHash()).isEqualTo(hashAntes);
        assertThat(encerrados).isEmpty();
        assertThat(publicados).isEmpty();
    }

    @Test
    void tres_senhas_atuais_erradas_bloqueiam_ate_a_senha_certa() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> trocar.executar("operador", "t", "errada-errada-123", NOVA, NOVA, ORIGEM))
                    .isInstanceOf(ParametroInvalidoException.class);
        }

        assertThatThrownBy(() -> trocar.executar("operador", "t", ATUAL, NOVA, NOVA, ORIGEM))
                .isInstanceOf(LimiteExcedidoException.class);
        assertThat(hashes.confere(ATUAL, mapa.get("operador").senhaHash())).isTrue();
    }

    @Test
    void confirmacao_diferente_senha_fraca_e_senha_igual_a_atual_sao_recusadas() {
        assertThatThrownBy(() -> trocar.executar("operador", "t", ATUAL, NOVA, "outra-coisa-qualquer", ORIGEM))
                .hasMessageContaining("confirmação");
        assertThatThrownBy(() -> trocar.executar("operador", "t", ATUAL, "curta", "curta", ORIGEM))
                .hasMessageContaining("12");
        assertThatThrownBy(() -> trocar.executar("operador", "t", ATUAL, "operador-operador", "operador-operador", ORIGEM))
                .hasMessageContaining("login");
        assertThatThrownBy(() -> trocar.executar("operador", "t", ATUAL, ATUAL, ATUAL, ORIGEM))
                .hasMessageContaining("diferente");

        assertThat(hashes.confere(ATUAL, mapa.get("operador").senhaHash())).isTrue();
        assertThat(encerrados).isEmpty();
    }

    @Test
    void usuario_desativado_ou_sem_senha_nao_troca() {
        mapa.put("sem-senha", new Usuario("sem-senha", "Sem Senha", Perfil.OPERADOR, null, true));
        mapa.put("inativo", new Usuario("inativo", "Inativo", Perfil.OPERADOR, null, false, hashes.gerar(ATUAL)));

        assertThatThrownBy(() -> trocar.executar("sem-senha", "t", ATUAL, NOVA, NOVA, ORIGEM))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> trocar.executar("inativo", "t", ATUAL, NOVA, NOVA, ORIGEM))
                .isInstanceOf(br.gov.mt.sema.rastro.shared.application.exception.NaoAutenticadoException.class);
    }
}
