package br.gov.mt.sema.rastro.acesso.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import br.gov.mt.sema.rastro.acesso.domain.HashDeSenha;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Usuários padrão por variável de ambiente (L1, bloco 2): com e sem variável, senha fraca, repetida e log limpo. */
class CriarUsuariosPadraoTest {

    private static final String S_OPERADOR = "senha-de-teste-op-01";
    private static final String S_ANALISTA = "senha-de-teste-an-02";
    private static final String S_ADMIN = "senha-de-teste-ad-03";
    private static final String CNPJ = "11.222.333/0001-81";
    private static final String CPF = "111.444.777-35";

    private static final class Cadastro implements CadastroDeUsuarios {
        final Map<String, Usuario> usuarios = new HashMap<>();

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

    private final HashDeSenha hashes = new HashDeSenha(1000);
    private final Cadastro cadastro = new Cadastro();
    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void capturarLog() {
        logger = (Logger) LoggerFactory.getLogger(CriarUsuariosPadrao.class);
        log.start();
        logger.addAppender(log);
    }

    @AfterEach
    void soltarLog() {
        logger.detachAppender(log);
    }

    private void subir(String operador, String cpfOperador, String analista, String cpfAnalista, String admin) {
        new CriarUsuariosPadrao(cadastro, hashes, operador, cpfOperador, analista, cpfAnalista, admin).criar();
    }

    private String mensagens() {
        return log.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void com_as_tres_variaveis_cria_os_tres_usuarios_com_perfil_e_hash_proprios() {
        subir(S_OPERADOR, CNPJ, S_ANALISTA, CPF, S_ADMIN);

        assertThat(cadastro.usuarios).containsOnlyKeys("operador", "analista", "admin");
        Usuario operador = cadastro.usuarios.get("operador");
        Usuario analista = cadastro.usuarios.get("analista");
        Usuario admin = cadastro.usuarios.get("admin");
        assertThat(operador.perfil()).isEqualTo(Perfil.OPERADOR);
        assertThat(operador.nome()).isEqualTo("Operador");
        assertThat(operador.cpfCnpj()).isEqualTo("11222333000181");
        assertThat(analista.perfil()).isEqualTo(Perfil.ANALISTA);
        assertThat(analista.cpfCnpj()).isEqualTo("11144477735");
        assertThat(admin.perfil()).isEqualTo(Perfil.ADMIN);
        assertThat(admin.nome()).isEqualTo("Administrador");
        assertThat(operador.ativo() && analista.ativo() && admin.ativo()).isTrue();
        assertThat(hashes.confere(S_OPERADOR, operador.senhaHash())).isTrue();
        assertThat(hashes.confere(S_ANALISTA, analista.senhaHash())).isTrue();
        assertThat(hashes.confere(S_ADMIN, admin.senhaHash())).isTrue();
        assertThat(hashes.confere(S_OPERADOR, admin.senhaHash())).isFalse();
        assertThat(log.list).isEmpty();
    }

    @Test
    void sem_nenhuma_variavel_nao_cria_usuario_e_avisa_so_o_nome_das_variaveis() {
        subir("", "", null, "", "   ");

        assertThat(cadastro.usuarios).isEmpty();
        assertThat(mensagens()).contains("RASTRO_SENHA_OPERADOR", "RASTRO_SENHA_ANALISTA", "RASTRO_SENHA_ADMIN");
        assertThat(log.list).anyMatch(e -> e.getLevel().toString().equals("ERROR")
                && e.getFormattedMessage().contains("nenhum administrador"));
    }

    @Test
    void cada_variavel_ausente_deixa_so_o_seu_usuario_de_fora() {
        subir("", CNPJ, S_ANALISTA, CPF, S_ADMIN);
        assertThat(cadastro.usuarios).containsOnlyKeys("analista", "admin");
        cadastro.usuarios.clear();

        subir(S_OPERADOR, CNPJ, "", CPF, S_ADMIN);
        assertThat(cadastro.usuarios).containsOnlyKeys("operador", "admin");
        cadastro.usuarios.clear();

        subir(S_OPERADOR, CNPJ, S_ANALISTA, CPF, "");
        assertThat(cadastro.usuarios).containsOnlyKeys("operador", "analista");
    }

    @Test
    void senha_fraca_nao_cria_o_usuario() {
        subir("curta", CNPJ, S_ANALISTA, CPF, S_ADMIN);

        assertThat(cadastro.usuarios).containsOnlyKeys("analista", "admin");
        assertThat(mensagens()).contains("RASTRO_SENHA_OPERADOR").contains("política");
    }

    @Test
    void senha_igual_ao_login_ou_que_o_contem_nao_cria_o_usuario() {
        subir("operador", CNPJ, "Minha-analista-9!", CPF, S_ADMIN);

        assertThat(cadastro.usuarios).containsOnlyKeys("admin");
        // 'operador' tem 8 caracteres (fraca) e a do analista contém o login
        assertThat(mensagens()).contains("RASTRO_SENHA_OPERADOR", "RASTRO_SENHA_ANALISTA");
    }

    @Test
    void duas_senhas_iguais_nao_criam_nenhum_dos_dois_e_o_aviso_traz_so_os_nomes() {
        subir(S_OPERADOR, CNPJ, S_OPERADOR, CPF, S_ADMIN);

        assertThat(cadastro.usuarios).containsOnlyKeys("admin");
        assertThat(mensagens()).contains("RASTRO_SENHA_OPERADOR", "RASTRO_SENHA_ANALISTA")
                .doesNotContain(S_OPERADOR);
    }

    @Test
    void senhas_que_so_diferem_na_caixa_contam_como_repetidas() {
        subir(S_OPERADOR, CNPJ, S_ANALISTA, CPF, S_OPERADOR.toUpperCase());

        assertThat(cadastro.usuarios).containsOnlyKeys("analista");
        assertThat(mensagens()).contains("RASTRO_SENHA_OPERADOR", "RASTRO_SENHA_ADMIN");
    }

    @Test
    void tres_senhas_iguais_nao_criam_ninguem() {
        subir(S_ADMIN, CNPJ, S_ADMIN, CPF, S_ADMIN);

        assertThat(cadastro.usuarios).isEmpty();
        assertThat(mensagens()).contains("RASTRO_SENHA_OPERADOR", "RASTRO_SENHA_ANALISTA", "RASTRO_SENHA_ADMIN");
    }

    @Test
    void operador_sem_cpf_cnpj_valido_nao_e_criado() {
        subir(S_OPERADOR, "", S_ANALISTA, CPF, S_ADMIN);
        assertThat(cadastro.usuarios).containsOnlyKeys("analista", "admin");
        assertThat(mensagens()).contains("RASTRO_OPERADOR_CPF_CNPJ");
        cadastro.usuarios.clear();

        subir(S_OPERADOR, "000.000.000-01", S_ANALISTA, CPF, S_ADMIN);
        assertThat(cadastro.usuarios).containsOnlyKeys("analista", "admin");
    }

    @Test
    void analista_sem_cpf_e_criado_e_avisa_que_a_caixa_fica_vazia() {
        subir(S_OPERADOR, CNPJ, S_ANALISTA, "", S_ADMIN);

        assertThat(cadastro.usuarios.get("analista").cpfCnpj()).isNull();
        assertThat(mensagens()).contains("RASTRO_ANALISTA_CPF");
    }

    @Test
    void analista_com_cpf_invalido_e_criado_sem_cpf() {
        subir(S_OPERADOR, CNPJ, S_ANALISTA, "000.000.000-01", S_ADMIN);

        assertThat(cadastro.usuarios.get("analista").cpfCnpj()).isNull();
    }

    @Test
    void nenhum_valor_de_senha_ou_documento_vai_para_o_log() {
        subir("curta", "000.000.000-01", S_ANALISTA, "000.000.000-01", S_ANALISTA);
        subir(S_OPERADOR, CNPJ, "x", CPF, "y");

        String tudo = mensagens();
        assertThat(tudo).isNotBlank().doesNotContain(S_ANALISTA).doesNotContain(S_OPERADOR).doesNotContain("curta")
                .doesNotContain("000.000.000-01").doesNotContain(CNPJ).doesNotContain(CPF);
    }
}
