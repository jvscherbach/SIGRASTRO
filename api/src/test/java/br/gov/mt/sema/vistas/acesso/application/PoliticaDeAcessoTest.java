package br.gov.mt.sema.vistas.acesso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import br.gov.mt.sema.vistas.acesso.domain.Perfil;
import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;
import br.gov.mt.sema.vistas.shared.application.exception.DocumentoSigilosoException;
import br.gov.mt.sema.vistas.shared.application.exception.NaoInteressadoException;
import br.gov.mt.sema.vistas.shared.application.exception.VistasException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Matriz da política de acesso (S4, bloco 1): perfil × interessado ou não × sigiloso ou não × dono do pedido ou não.
 * Dados fictícios.
 */
class PoliticaDeAcessoTest {

    private static final String CNPJ_DO_OPERADOR = "12.345.678/0001-90";
    private static final String CNPJ_DE_OUTRO = "98.765.432/0001-10";

    private Usuario usuario;
    private final PoliticaDeAcesso politica = new PoliticaDeAcesso(new UsuarioAtual() {
        @Override
        public Usuario obter() {
            return usuario;
        }

        @Override
        public String token() {
            return "token-de-teste";
        }
    });

    private static Usuario usuario(Perfil perfil) {
        return switch (perfil) {
            case OPERADOR -> new Usuario("operador.demo", "Operador Demo", perfil, CNPJ_DO_OPERADOR, true);
            case OPERADOR_SIGILO -> new Usuario("gestor.demo", "Gestor Demo", perfil, null, true);
            case ADMIN -> new Usuario("admin.demo", "Admin Demo", perfil, null, true);
        };
    }

    /** Perfil, interessado (o requerimento é do Operador?), sigiloso, dono do pedido. */
    static Stream<Arguments> matriz() {
        List<Arguments> casos = new ArrayList<>();
        for (Perfil perfil : Perfil.values()) {
            for (boolean interessado : new boolean[] {true, false}) {
                for (boolean sigiloso : new boolean[] {true, false}) {
                    for (boolean dono : new boolean[] {true, false}) {
                        casos.add(Arguments.of(perfil, interessado, sigiloso, dono));
                    }
                }
            }
        }
        return casos.stream();
    }

    @ParameterizedTest(name = "{0}, interessado={1}, sigiloso={2}, dono={3}")
    @MethodSource("matriz")
    void matrizCompleta(Perfil perfil, boolean interessado, boolean sigiloso, boolean dono) {
        usuario = usuario(perfil);
        String cpfCnpjDoInteressado = interessado ? CNPJ_DO_OPERADOR : CNPJ_DE_OUTRO;
        PedidoDeUsuario pedido = new PedidoDeUsuario(dono ? usuario.login() : "outro.usuario", cpfCnpjDoInteressado);
        boolean operador = perfil == Perfil.OPERADOR;

        // RN07: Operador só como interessado; Gestor e Administrador, tudo.
        boolean esperaRequerimento = !operador || interessado;
        assertThat(politica.podeAcessarRequerimento(usuario, cpfCnpjDoInteressado)).isEqualTo(esperaRequerimento);
        assertResultado(() -> politica.exigirAcessoAoRequerimento(cpfCnpjDoInteressado), esperaRequerimento,
                ErrorCode.NAO_INTERESSADO);

        // RF20: sigiloso só para Gestor e Administrador.
        boolean esperaDocumento = !sigiloso || !operador;
        assertThat(politica.podeAcessarDocumento(usuario, sigiloso)).isEqualTo(esperaDocumento);
        assertThat(politica.podeAcessarDocumento(sigiloso)).isEqualTo(esperaDocumento);
        assertResultado(() -> politica.exigirAcessoAoDocumento(sigiloso), esperaDocumento, ErrorCode.DOCUMENTO_SIGILOSO);
        assertThat(PoliticaDeAcesso.mascararTitulo(esperaDocumento, "Laudo reservado"))
                .isEqualTo(esperaDocumento ? "Laudo reservado" : PoliticaDeAcesso.TITULO_SIGILOSO);
        assertThat(PoliticaDeAcesso.mascararDadoDoArquivo(esperaDocumento, "laudo-secreto.pdf"))
                .isEqualTo(esperaDocumento ? "laudo-secreto.pdf" : null);

        // Pedido: o dono enquanto tiver acesso ao requerimento; Gestor e Administrador, todos.
        boolean esperaPedido = operador ? dono && interessado : true;
        assertThat(politica.podeAcessarPedido(usuario, pedido)).isEqualTo(esperaPedido);
        assertThat(politica.podeAcessarPedido(pedido)).isEqualTo(esperaPedido);
    }

    @Test
    void sigilosoEResponsavelPelaTarefaSoParaGestorEAdministrador() {
        assertThat(politica.podeVerDocumentoSigiloso(usuario(Perfil.OPERADOR))).isFalse();
        assertThat(politica.podeVerDocumentoSigiloso(usuario(Perfil.OPERADOR_SIGILO))).isTrue();
        assertThat(politica.podeVerDocumentoSigiloso(usuario(Perfil.ADMIN))).isTrue();
        assertThat(politica.podeVerResponsavelTarefa(usuario(Perfil.OPERADOR))).isFalse();
        assertThat(politica.podeVerResponsavelTarefa(usuario(Perfil.OPERADOR_SIGILO))).isTrue();
        assertThat(politica.podeVerResponsavelTarefa(usuario(Perfil.ADMIN))).isTrue();
    }

    @Test
    void operadorSemCpfCnpjNaoAcessaNadaNemOProprioPedido() {
        usuario = new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, " ", true);

        assertThat(politica.podeAcessarRequerimento(usuario, CNPJ_DO_OPERADOR)).isFalse();
        assertThat(politica.podeAcessarRequerimento(usuario, null)).isFalse();
        assertThat(politica.podeAcessarPedido(new PedidoDeUsuario("operador.demo", CNPJ_DO_OPERADOR))).isFalse();
        assertThat(politica.restricaoDoUsuarioAtual().semAcessoAlgum()).isTrue();
    }

    @Test
    void requerimentoSemInteressadoSoParaGestorEAdministrador() {
        assertThat(politica.podeAcessarRequerimento(usuario(Perfil.OPERADOR), null)).isFalse();
        assertThat(politica.podeAcessarRequerimento(usuario(Perfil.OPERADOR_SIGILO), null)).isTrue();
        assertThat(politica.podeAcessarRequerimento(usuario(Perfil.ADMIN), null)).isTrue();
    }

    @Test
    void cpfCnpjComparadoSoPelosDigitos() {
        usuario = usuario(Perfil.OPERADOR);

        assertThat(politica.podeAcessarRequerimento(usuario, "12345678000190")).isTrue();
        assertThat(politica.podeAcessarPedido(new PedidoDeUsuario("operador.demo", "12345678000190"))).isTrue();
    }

    @Test
    void operadorDonoComOutroCpfCnpjPerdeOAcessoAoPedido() {
        // Reforço do bloco 1: o pedido guarda o interessado; se o vínculo do usuário mudar, o pedido some para ele.
        usuario = new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, CNPJ_DE_OUTRO, true);

        assertThat(politica.podeAcessarPedido(new PedidoDeUsuario("operador.demo", CNPJ_DO_OPERADOR))).isFalse();
    }

    @Test
    void restricaoDoUsuarioAtualSoParaOperador() {
        usuario = usuario(Perfil.OPERADOR);
        assertThat(politica.restricaoDoUsuarioAtual().restrita()).isTrue();
        assertThat(politica.restricaoDoUsuarioAtual().digitos()).isEqualTo("12345678000190");
        usuario = usuario(Perfil.OPERADOR_SIGILO);
        assertThat(politica.restricaoDoUsuarioAtual().restrita()).isFalse();
        usuario = usuario(Perfil.ADMIN);
        assertThat(politica.restricaoDoUsuarioAtual().restrita()).isFalse();
    }

    @Test
    void excecoesMantemOContratoESemDadoDoRecurso() {
        usuario = usuario(Perfil.OPERADOR);

        assertThatThrownBy(() -> politica.exigirAcessoAoRequerimento(CNPJ_DE_OUTRO))
                .isInstanceOf(NaoInteressadoException.class)
                .hasMessageNotContaining("98").hasMessageNotContaining("765");
        assertThatThrownBy(() -> politica.exigirAcessoAoDocumento(true))
                .isInstanceOf(DocumentoSigilosoException.class);
    }

    private static void assertResultado(Runnable acao, boolean permitido, ErrorCode esperado) {
        if (permitido) {
            assertThatCode(acao::run).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(acao::run).isInstanceOfSatisfying(VistasException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(esperado));
        }
    }
}
