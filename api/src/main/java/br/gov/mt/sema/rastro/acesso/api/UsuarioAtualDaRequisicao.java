package br.gov.mt.sema.rastro.acesso.api;

import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.shared.application.exception.NaoAutenticadoException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Component
class UsuarioAtualDaRequisicao implements UsuarioAtual {

    @Override
    public Usuario obter() {
        if (atributo(AutenticacaoFilter.ATRIBUTO_USUARIO) instanceof Usuario usuario) {
            return usuario;
        }
        throw new NaoAutenticadoException("Sessão ausente, inválida ou expirada.");
    }

    @Override
    public String token() {
        if (atributo(AutenticacaoFilter.ATRIBUTO_TOKEN) instanceof String token) {
            return token;
        }
        throw new NaoAutenticadoException("Sessão ausente, inválida ou expirada.");
    }

    private static Object atributo(String nome) {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        return atributos == null ? null : atributos.getAttribute(nome, RequestAttributes.SCOPE_REQUEST);
    }
}
