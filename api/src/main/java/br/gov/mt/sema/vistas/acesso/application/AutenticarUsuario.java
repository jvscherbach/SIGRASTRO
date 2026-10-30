package br.gov.mt.sema.vistas.acesso.application;

import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.shared.application.exception.NaoAutenticadoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AutenticarUsuario {

    private final RepositorioUsuarios usuarios;
    private final RepositorioSessoes sessoes;
    private final ObterUsuarioAtual obterUsuarioAtual;
    private final ApplicationEventPublisher eventos;

    public AutenticarUsuario(RepositorioUsuarios usuarios, RepositorioSessoes sessoes,
            ObterUsuarioAtual obterUsuarioAtual, ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.obterUsuarioAtual = obterUsuarioAtual;
        this.eventos = eventos;
    }

    public SessaoAberta executar(String login, String senha) {
        return executar(login, senha, null);
    }

    /** @param tokenAnterior token da sessão que o pedido já trazia (pode ser nulo ou inválido: é só informação) */
    public SessaoAberta executar(String login, String senha, String tokenAnterior) {
        Usuario usuario = usuarios.autenticar(login, senha)
                .filter(Usuario::ativo)
                .orElseThrow(() -> new NaoAutenticadoException("Usuário ou senha inválidos."));
        Sessao sessao = sessoes.abrir(usuario.login());
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
}
