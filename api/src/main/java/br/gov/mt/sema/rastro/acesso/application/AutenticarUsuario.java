package br.gov.mt.sema.rastro.acesso.application;

import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.domain.HashDeSenha;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.shared.application.exception.NaoAutenticadoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AutenticarUsuario {

    /** A mesma mensagem para usuário inexistente, desativado, sem senha e senha errada. */
    public static final String MENSAGEM_INVALIDA = "Usuário ou senha inválidos.";

    private final RepositorioUsuarios usuarios;
    private final RepositorioSessoes sessoes;
    private final ObterUsuarioAtual obterUsuarioAtual;
    private final ApplicationEventPublisher eventos;
    private final HashDeSenha hashes;
    private final LimiteDeTentativas limite;

    public AutenticarUsuario(RepositorioUsuarios usuarios, RepositorioSessoes sessoes,
            ObterUsuarioAtual obterUsuarioAtual, ApplicationEventPublisher eventos, HashDeSenha hashes,
            LimiteDeTentativas limite) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.obterUsuarioAtual = obterUsuarioAtual;
        this.eventos = eventos;
        this.hashes = hashes;
        this.limite = limite;
    }

    public SessaoAberta executar(String login, String senha) {
        return executar(login, senha, null, null);
    }

    public SessaoAberta executar(String login, String senha, String tokenAnterior) {
        return executar(login, senha, tokenAnterior, null);
    }

    /**
     * @param tokenAnterior token da sessão que o pedido já trazia (pode ser nulo ou inválido: é só informação)
     * @param origem IP de origem do pedido (nulo = desconhecida); o limite de tentativas conta por login + origem
     */
    public SessaoAberta executar(String login, String senha, String tokenAnterior, String origem) {
        limite.exigirLiberado(login, origem);
        Optional<Usuario> candidato = usuarios.buscarPorLogin(login)
                .filter(Usuario::ativo)
                .filter(u -> u.senhaHash() != null);
        // Usuário inexistente, desativado ou sem senha percorre o mesmo caminho, com o hash falso: mesmo trabalho.
        String hash = candidato.map(Usuario::senhaHash).orElseGet(hashes::hashFalso);
        boolean confere = hashes.confere(senha, hash);
        if (candidato.isEmpty() || !confere) {
            boolean iniciouBloqueio = limite.registrarFalha(login, origem);
            publicarRecusa(new LoginRecusado(iniciouBloqueio));
            throw new NaoAutenticadoException(MENSAGEM_INVALIDA);
        }
        Usuario usuario = candidato.get();
        limite.registrarAcerto(login, origem);
        Sessao sessao = sessoes.abrir(usuario.login());
        usuarios.registrarAcesso(usuario.login());
        publicar(new EntradaRealizada(usuario, anterior(tokenAnterior)));
        return new SessaoAberta(sessao.token(), sessao.expiraEm(), usuario);
    }

    private Usuario anterior(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return obterUsuarioAtual.porToken(token);
        } catch (NaoAutenticadoException e) {
            return null;
        }
    }

    /** A entrada já aconteceu: nada que um ouvinte faça pode derrubar o login. */
    private void publicar(EntradaRealizada entrada) {
        try {
            eventos.publishEvent(entrada);
        } catch (RuntimeException e) {
            // o ouvinte (auditoria) já registra a própria falha; o login segue
        }
    }

    private void publicarRecusa(LoginRecusado recusa) {
        try {
            eventos.publishEvent(recusa);
        } catch (RuntimeException e) {
            // a recusa é a mesma com ou sem auditoria
        }
    }
}
