package br.gov.mt.sema.vistas.acesso.application;

import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.shared.application.exception.NaoAutenticadoException;
import org.springframework.stereotype.Service;

@Service
public class ObterUsuarioAtual {

    private final RepositorioUsuarios usuarios;
    private final RepositorioSessoes sessoes;

    public ObterUsuarioAtual(RepositorioUsuarios usuarios, RepositorioSessoes sessoes) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
    }

    public Usuario porToken(String token) {
        return sessoes.buscar(token)
                .flatMap(sessao -> usuarios.buscarPorLogin(sessao.login()))
                .filter(Usuario::ativo)
                .orElseThrow(() -> new NaoAutenticadoException("Sessão ausente, inválida ou expirada."));
    }
}
