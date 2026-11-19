package br.gov.mt.sema.rastro.acesso.application;

import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.shared.application.exception.NaoAutenticadoException;

/** Contrato do módulo acesso para os demais módulos: quem está autenticado na requisição corrente. */
public interface UsuarioAtual {

    /** Lança NaoAutenticadoException quando a requisição não tem usuário autenticado. */
    Usuario obter();

    String token();

    /** O usuário autenticado, se houver (rotas que aceitam outra prova de acesso, como o ticket de download). */
    default Optional<Usuario> talvez() {
        try {
            return Optional.of(obter());
        } catch (NaoAutenticadoException e) {
            return Optional.empty();
        }
    }
}
