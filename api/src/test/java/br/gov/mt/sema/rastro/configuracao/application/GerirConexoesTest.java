package br.gov.mt.sema.rastro.configuracao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.application.port.RequisicaoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.configuracao.application.GerirConexoes.TesteDaConexao;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import br.gov.mt.sema.rastro.shared.application.exception.RastroException;
import org.junit.jupiter.api.Test;

class GerirConexoesTest {

    private static final UsuarioAtual USUARIO = new UsuarioAtual() {
        @Override
        public Usuario obter() {
            return new Usuario("admin.demo", "Admin Demo", Perfil.ADMIN, null, true);
        }

        @Override
        public String token() {
            return "token";
        }
    };
    private static final PoliticaDeAcesso ADMIN = new PoliticaDeAcesso(USUARIO);

    private record Falsa(Estado estado, Supplier<Resultado> acao) implements VerificadorDeConexao {

        @Override
        public String nome() {
            return "falsa";
        }

        @Override
        public String rotulo() {
            return "Falsa";
        }

        @Override
        public Resultado verificar() {
            return acao.get();
        }

        @Override
        public Optional<Long> horasAteExpirar() {
            throw new IllegalStateException("token-que-nao-pode-sair");
        }
    }

    private final List<NovoEvento> eventos = new java.util.concurrent.CopyOnWriteArrayList<>();

    private GerirConexoes conexoes(VerificadorDeConexao verificador, Duration limite) {
        ContextoDoEvento contexto = new ContextoDoEvento(USUARIO, new RequisicaoDoEvento() {
            @Override
            public String origem() {
                return null;
            }

            @Override
            public String correlationId() {
                return null;
            }
        });
        return new GerirConexoes(List.of(verificador), ADMIN, 15, limite, eventos::add, contexto);
    }

    @Test
    void classificacao_codigo_e_tempo_limite_chegam_ao_resultado_e_a_auditoria_registra_so_conexao_e_resultado() {
        TesteDaConexao teste = conexoes(new Falsa(VerificadorDeConexao.Estado.CONFIGURADA,
                () -> VerificadorDeConexao.Resultado.deHttp(404, "uma chave fictícia", "token")), Duration.ofSeconds(5))
                .testar("falsa");

        assertThat(teste.classificacao()).isEqualTo(VerificadorDeConexao.Classificacao.ALCANCAVEL_COM_RESSALVA);
        assertThat(teste.sucesso()).isTrue();
        assertThat(teste.codigoHttp()).isEqualTo(404);
        assertThat(teste.tempoLimiteSegundos()).isEqualTo(15);
        assertThat(eventos).singleElement().satisfies(evento -> {
            assertThat(evento.tipo()).isEqualTo(TipoDeEvento.CONEXAO);
            assertThat(evento.detalhe()).startsWith("Falsa: ALCANCAVEL_COM_RESSALVA (HTTP 404), ").endsWith(" ms");
        });
    }

    @Test
    void falha_de_autenticacao_nao_e_sucesso() {
        TesteDaConexao teste = conexoes(new Falsa(VerificadorDeConexao.Estado.CONFIGURADA,
                () -> VerificadorDeConexao.Resultado.deHttp(401, "uma chave fictícia", "token")), Duration.ofSeconds(5))
                .testar("falsa");

        assertThat(teste.classificacao()).isEqualTo(VerificadorDeConexao.Classificacao.FALHA_DE_AUTENTICACAO);
        assertThat(teste.sucesso()).isFalse();
    }

    @Test
    void auditoria_que_falha_nao_derruba_o_teste() {
        ContextoDoEvento quebrado = new ContextoDoEvento(USUARIO, null);
        GerirConexoes conexoes = new GerirConexoes(List.of(new Falsa(VerificadorDeConexao.Estado.CONFIGURADA,
                () -> new VerificadorDeConexao.Resultado(true, "ok"))), ADMIN, 15, Duration.ofSeconds(5), evento -> {
                    throw new IllegalStateException("auditoria fora");
                }, quebrado);

        assertThat(conexoes.testar("falsa").sucesso()).isTrue();
    }

    @Test
    void teto_do_teste_cobre_duas_chamadas_e_a_espera() {
        assertThat(GerirConexoes.teto(15)).isEqualTo(Duration.ofSeconds(35));
    }

    @Test
    void sucesso_com_tempo_e_sem_horas_quando_a_leitura_falha() {
        TesteDaConexao teste = conexoes(new Falsa(VerificadorDeConexao.Estado.CONFIGURADA,
                () -> new VerificadorDeConexao.Resultado(true, "Conexão estabelecida.")), Duration.ofSeconds(5)).testar("falsa");

        assertThat(teste.sucesso()).isTrue();
        assertThat(teste.tempoMs()).isNotNegative();
        assertThat(teste.horasAteExpirar()).isNull();
    }

    @Test
    void excecao_vira_mensagem_generica() {
        TesteDaConexao teste = conexoes(new Falsa(VerificadorDeConexao.Estado.CONFIGURADA, () -> {
            throw new IllegalStateException("https://host.invalido/segredo");
        }), Duration.ofSeconds(5)).testar("falsa");

        assertThat(teste.sucesso()).isFalse();
        assertThat(teste.mensagem()).isEqualTo("Não foi possível testar a conexão.");
    }

    @Test
    void tempo_limite() {
        TesteDaConexao teste = conexoes(new Falsa(VerificadorDeConexao.Estado.CONFIGURADA, () -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new VerificadorDeConexao.Resultado(true, "tarde demais");
        }), Duration.ofMillis(100)).testar("falsa");

        assertThat(teste.sucesso()).isFalse();
        assertThat(teste.mensagem()).isEqualTo("Tempo limite do teste (15 s) excedido.");
    }

    @Test
    void nao_configurada_nem_chama() {
        TesteDaConexao teste = conexoes(new Falsa(VerificadorDeConexao.Estado.NAO_CONFIGURADA, () -> {
            throw new AssertionError("não deveria chamar");
        }), Duration.ofSeconds(5)).testar("falsa");

        assertThat(teste.sucesso()).isFalse();
        assertThat(teste.mensagem()).isEqualTo("Conexão não configurada.");
    }

    @Test
    void nome_fora_da_lista_e_404() {
        GerirConexoes conexoes = conexoes(new Falsa(VerificadorDeConexao.Estado.CONFIGURADA, () -> null), Duration.ofSeconds(1));

        assertThatThrownBy(() -> conexoes.testar("http://host.invalido")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void um_teste_por_conexao_de_cada_vez() throws Exception {
        CountDownLatch comecou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        GerirConexoes conexoes = conexoes(new Falsa(VerificadorDeConexao.Estado.CONFIGURADA, () -> {
            comecou.countDown();
            try {
                liberar.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new VerificadorDeConexao.Resultado(true, "ok");
        }), Duration.ofSeconds(10));
        ExecutorService outra = Executors.newSingleThreadExecutor();
        try {
            Future<TesteDaConexao> primeiro = outra.submit(() -> conexoes.testar("falsa"));
            comecou.await();

            assertThatThrownBy(() -> conexoes.testar("falsa")).isInstanceOfSatisfying(RastroException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TESTE_DE_CONEXAO_EM_ANDAMENTO));

            liberar.countDown();
            assertThat(primeiro.get().sucesso()).isTrue();
            assertThat(conexoes.testar("falsa").sucesso()).isTrue();
        } finally {
            liberar.countDown();
            outra.shutdownNow();
        }
    }
}
