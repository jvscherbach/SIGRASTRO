package br.gov.mt.sema.rastro.auditoria.application;

import br.gov.mt.sema.rastro.acesso.application.EntradaRealizada;
import br.gov.mt.sema.rastro.acesso.application.LoginRecusado;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.UsuarioDoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Eventos do grupo ACESSOS que nascem do login: {@code LOGIN} em toda entrada e {@code PERFIL} quando o perfil da nova
 * entrada difere do da sessão que já estava aberta. O usuário é o que acabou de entrar (o login ainda não tem sessão na
 * requisição). Nunca lança: falha de auditoria não impede o login.
 */
@Component
class EventosDeAcesso {

    private static final Logger log = LoggerFactory.getLogger(EventosDeAcesso.class);

    private static final String LOGIN_ANONIMO = "(nao-autenticado)";

    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;

    EventosDeAcesso(RegistrarEvento registrar, ContextoDoEvento contexto) {
        this.registrar = registrar;
        this.contexto = contexto;
    }

    @EventListener
    void aoEntrar(EntradaRealizada entrada) {
        try {
            UsuarioDoEvento quem = retrato(entrada.usuario());
            registrar.registrar(contexto.novo(TipoDeEvento.LOGIN, quem).criar());
            if (entrada.trocouDePerfil()) {
                registrar.registrar(contexto.novo(TipoDeEvento.PERFIL, quem)
                        .detalhe("Perfil novo: " + entrada.usuario().rotuloDoPerfil())
                        .criar());
            }
        } catch (RuntimeException e) {
            log.error("Falha ao registrar evento de acesso: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Falha de login e bloqueio (L1). O evento não leva o login digitado (pode ser uma senha digitada no campo errado) nem
     * diz se ele existe: o usuário é o mesmo marcador para qualquer falha, e a origem vem da requisição.
     */
    @EventListener
    void aoRecusar(LoginRecusado recusa) {
        try {
            UsuarioDoEvento anonimo = new UsuarioDoEvento(LOGIN_ANONIMO, "Não autenticado", "Sem perfil");
            registrar.registrar(contexto.novo(TipoDeEvento.LOGIN, anonimo).detalhe("Falha de login").criar());
            if (recusa.iniciouBloqueio()) {
                registrar.registrar(contexto.novo(TipoDeEvento.LOGIN, anonimo)
                        .detalhe("Entrada bloqueada por excesso de tentativas").criar());
            }
        } catch (RuntimeException e) {
            log.error("Falha ao registrar evento de acesso: {}", e.getClass().getSimpleName());
        }
    }

    private static UsuarioDoEvento retrato(Usuario usuario) {
        return new UsuarioDoEvento(usuario.login(), usuario.nome(), usuario.rotuloDoPerfil());
    }
}
