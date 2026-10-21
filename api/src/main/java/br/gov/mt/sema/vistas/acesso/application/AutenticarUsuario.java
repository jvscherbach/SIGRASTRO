package br.gov.mt.sema.vistas.acesso.application;

import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.shared.application.exception.NaoAutenticadoException;
import org.springframework.stereotype.Service;

@Service
public class AutenticarUsuario {

    private final RepositorioUsuarios usuarios;
    private final RepositorioSessoes sessoes;

    public AutenticarUsuario(RepositorioUsuarios usuarios, RepositorioSessoes sessoes) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
    }

    public SessaoAberta executar(String login, String senha) {
        Usuario usuario = usuarios.autenticar(login, senha)
                .filter(Usuario::ativo)
                .orElseThrow(() -> new NaoAutenticadoException("Usuário ou senha inválidos."));
        Sessao sessao = sessoes.abrir(usuario.login());
        return new SessaoAberta(sessao.token(), sessao.expiraEm(), usuario);
    }
}
