package br.gov.mt.sema.vistas.acesso.application;

import br.gov.mt.sema.vistas.acesso.domain.Usuario;

/** Contrato do módulo acesso para os demais módulos: quem está autenticado na requisição corrente. */
public interface UsuarioAtual {

    /** Lança NaoAutenticadoException quando a requisição não tem usuário autenticado. */
    Usuario obter();

    String token();
}
