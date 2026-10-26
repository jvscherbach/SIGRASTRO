package br.gov.mt.sema.vistas.acesso.application;

import org.springframework.stereotype.Service;

/** Contrato do módulo acesso para os demais módulos consultarem o que o perfil do usuário da requisição pode ver. */
@Service
public class ObterPermissoesDoUsuario {

    private final UsuarioAtual usuarioAtual;

    public ObterPermissoesDoUsuario(UsuarioAtual usuarioAtual) {
        this.usuarioAtual = usuarioAtual;
    }

    public boolean podeVerResponsavelTarefa() {
        return usuarioAtual.obter().perfil().podeVerResponsavelTarefa();
    }

    /** RF20: só Gestor e Administrador visualizam, selecionam e incluem no dossiê documentos sigilosos. */
    public boolean podeVerSigilosos() {
        return usuarioAtual.obter().perfil().podeVerSigilosos();
    }
}
