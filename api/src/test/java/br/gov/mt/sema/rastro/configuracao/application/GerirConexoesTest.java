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
import br.gov.mt.sema.rastro.configuracao.application.GerirConexoes.TesteDaConexao;
import br.gov.mt.sema.rastro.configuracao.application.port.VerificadorDeConexao;
import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import br.gov.mt.sema.rastro.shared.application.exception.RastroException;
import org.junit.jupiter.api.Test;

class GerirConexoesTest {

    private static final PoliticaDeAcesso ADMIN = new PoliticaDeAcesso(new UsuarioAtual() {
        @Override
        public Usuario obter() {
            return new Usuario("admin.demo", "Admin Demo", Perfil.ADMIN, null, true);
        }

        @Override
        public String token() {
            return "token";
        }
    });

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

    private static GerirConexoes conexoes(VerificadorDeConexao verificador, Duration limite) {
        return new GerirConexoes(List.of(verificador), ADMIN, limite);
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
        assertThat(teste.mensagem()).isEqualTo("Tempo limite excedido.");
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
