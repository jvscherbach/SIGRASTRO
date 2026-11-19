package br.gov.mt.sema.rastro.auditoria.domain;

/**
 * Retrato do usuário no momento do evento (sem vínculo com o cadastro). A construção só limpa e nunca lança: quem chama
 * não pode ser derrubado pela auditoria; os obrigatórios são cobrados em {@link EventoDeAuditoria}.
 *
 * @param perfil rótulo do perfil, como texto
 */
public record UsuarioDoEvento(String login, String nome, String perfil) {

    private static final int MAX_LOGIN = 100;
    private static final int MAX_NOME = 200;
    private static final int MAX_PERFIL = 40;

    public UsuarioDoEvento {
        login = TextoDoEvento.limpar(login, MAX_LOGIN);
        nome = TextoDoEvento.limpar(nome, MAX_NOME);
        perfil = TextoDoEvento.limpar(perfil, MAX_PERFIL);
    }
}
