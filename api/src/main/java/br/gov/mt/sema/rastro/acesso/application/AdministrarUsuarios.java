package br.gov.mt.sema.rastro.acesso.application;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import br.gov.mt.sema.rastro.acesso.domain.CpfCnpjDoUsuario;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import br.gov.mt.sema.rastro.shared.application.exception.RastroException;
import org.springframework.stereotype.Service;

/**
 * Cadastro de usuários e perfis (RF25, docs/12). Fica em {@code acesso} porque é o único módulo que conhece
 * {@link Perfil}; {@code configuracao} confere a permissão, chama este caso de uso e registra o evento. Inclusão e
 * alteração são serializadas: a conferência do último Administrador e a gravação não podem se intercalar. O efeito nas
 * sessões abertas é imediato porque cada requisição relê o usuário do cadastro ({@link ObterUsuarioAtual}).
 */
@Service
public class AdministrarUsuarios {

    private static final Pattern LOGIN = Pattern.compile("[a-z0-9][a-z0-9._-]{2,59}");
    private static final int MAX_NOME = 200;

    private final CadastroDeUsuarios cadastro;

    public AdministrarUsuarios(CadastroDeUsuarios cadastro) {
        this.cadastro = cadastro;
    }

    /** O antes e o depois de uma alteração. */
    public record Alteracao(UsuarioAdministrado antes, UsuarioAdministrado depois) {
    }

    public List<UsuarioAdministrado> listar() {
        return cadastro.listar().stream().sorted(Comparator.comparing(Usuario::nome, String.CASE_INSENSITIVE_ORDER))
                .map(this::administrado).toList();
    }

    public synchronized UsuarioAdministrado incluir(String nome, String login, String cpfCnpj, String perfil) {
        String nomeLimpo = nome == null ? "" : nome.strip();
        if (nomeLimpo.isEmpty() || nomeLimpo.length() > MAX_NOME) {
            throw new ParametroInvalidoException("nome", "Informe o nome, com até " + MAX_NOME + " caracteres.");
        }
        String loginNormalizado = login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
        if (!LOGIN.matcher(loginNormalizado).matches()) {
            throw new ParametroInvalidoException("login",
                    "O login tem de 3 a 60 caracteres: letras, números, ponto, hífen ou sublinhado.");
        }
        Perfil novoPerfil = perfil(perfil);
        String digitos = CpfCnpjDoUsuario.somenteDigitos(cpfCnpj);
        if (digitos.isEmpty() && novoPerfil.restritoAoInteressado()) {
            throw new ParametroInvalidoException("cpfCnpj", "O CPF/CNPJ é obrigatório para este perfil.");
        }
        if (!digitos.isEmpty() && !CpfCnpjDoUsuario.valido(digitos)) {
            throw new ParametroInvalidoException("cpfCnpj", "CPF/CNPJ inválido.");
        }
        if (cadastro.buscarPorLogin(loginNormalizado).isPresent()) {
            throw new RastroException(ErrorCode.LOGIN_JA_EXISTE, "Já existe um usuário com este login.");
        }
        Usuario novo = new Usuario(loginNormalizado, nomeLimpo, novoPerfil, digitos.isEmpty() ? null : digitos, true);
        cadastro.salvar(novo);
        return administrado(novo);
    }

    /**
     * @param perfil código do novo perfil; {@code null} mantém
     * @param ativo nova situação; {@code null} mantém
     * @param loginDoAutor quem está alterando: ninguém desativa o próprio usuário
     */
    public synchronized Alteracao alterar(String login, String perfil, Boolean ativo, String loginDoAutor) {
        Usuario atual = cadastro.buscarPorLogin(login)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        Perfil novoPerfil = perfil == null ? atual.perfil() : perfil(perfil);
        boolean novaSituacao = ativo == null ? atual.ativo() : ativo;
        if (atual.ativo() && !novaSituacao && atual.login().equals(loginDoAutor)) {
            throw new RastroException(ErrorCode.PROPRIO_USUARIO, "Não é possível desativar o próprio usuário.");
        }
        if (novoPerfil.restritoAoInteressado() && CpfCnpjDoUsuario.somenteDigitos(atual.cpfCnpj()).isEmpty()) {
            throw new ParametroInvalidoException("perfil",
                    "Este usuário não tem CPF/CNPJ cadastrado, obrigatório para o perfil escolhido.");
        }
        boolean deixaDeAdministrar = atual.perfil() == Perfil.ADMIN && atual.ativo()
                && (novoPerfil != Perfil.ADMIN || !novaSituacao);
        if (deixaDeAdministrar && administradoresAtivos() == 1) {
            throw new RastroException(ErrorCode.ULTIMO_ADMINISTRADOR,
                    "Não é possível desativar nem rebaixar o último Administrador ativo.");
        }
        Usuario alterado = new Usuario(atual.login(), atual.nome(), novoPerfil, atual.cpfCnpj(), novaSituacao);
        if (!alterado.equals(atual)) {
            cadastro.salvar(alterado);
        }
        return new Alteracao(administrado(atual), administrado(alterado));
    }

    private long administradoresAtivos() {
        return cadastro.listar().stream().filter(u -> u.ativo() && u.perfil() == Perfil.ADMIN).count();
    }

    private static Perfil perfil(String codigo) {
        return Arrays.stream(Perfil.values()).filter(p -> p.name().equals(codigo)).findFirst()
                .orElseThrow(() -> new ParametroInvalidoException("perfil", "Use OPERADOR, OPERADOR_SIGILO, ANALISTA ou ADMIN."));
    }

    private UsuarioAdministrado administrado(Usuario usuario) {
        return new UsuarioAdministrado(usuario.login(), usuario.nome(), usuario.perfil().name(), usuario.rotuloDoPerfil(),
                usuario.ativo(), cadastro.ultimoAcesso(usuario.login()).orElse(null),
                // S11: o CPF do Analista é o vínculo com o Camunda e nunca sai em resposta, nem mascarado
                usuario.perfil().podeVerCaixa() ? null : CpfCnpjDoUsuario.mascarar(usuario.cpfCnpj()));
    }
}
