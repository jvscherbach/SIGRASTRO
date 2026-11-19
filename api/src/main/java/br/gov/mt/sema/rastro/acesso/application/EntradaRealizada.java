package br.gov.mt.sema.rastro.acesso.application;

import br.gov.mt.sema.rastro.acesso.domain.Usuario;

/**
 * Publicado quando um login dá certo (login recusado não publica nada). {@code anterior} é o usuário da sessão que o
 * pedido de login já trazia, quando havia uma válida: base da "troca de perfil" do protótipo. Quem registra auditoria
 * ouve este evento; o módulo acesso não depende dela.
 */
public record EntradaRealizada(Usuario usuario, Usuario anterior) {

    /** Entrou com um perfil diferente do da sessão que já estava aberta. */
    public boolean trocouDePerfil() {
        return anterior != null && anterior.perfil() != usuario.perfil();
    }
}
