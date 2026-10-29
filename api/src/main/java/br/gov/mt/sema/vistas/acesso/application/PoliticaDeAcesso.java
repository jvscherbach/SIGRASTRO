package br.gov.mt.sema.vistas.acesso.application;

import br.gov.mt.sema.vistas.acesso.domain.RestricaoDeInteressado;
import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.shared.application.exception.DocumentoSigilosoException;
import br.gov.mt.sema.vistas.shared.application.exception.NaoInteressadoException;
import org.springframework.stereotype.Service;

/**
 * Política de acesso única (S4, RNF10): o único lugar que decide, pelo perfil e pelo CPF/CNPJ do usuário, quem acessa
 * requerimento (RN07), documento sigiloso (RF20), responsável pela tarefa (RN12) e pedido de dossiê. Os casos de uso
 * dos demais módulos perguntam aqui; não comparam perfil nem CPF/CNPJ por conta própria (ArchitectureTest).
 *
 * <p>Os métodos com {@link Usuario} são a regra pura; os sem ele aplicam a regra ao usuário da requisição. Os
 * {@code exigir...} lançam as exceções de contrato ({@code NAO_INTERESSADO}, {@code DOCUMENTO_SIGILOSO}); a resposta
 * 404 de recurso inexistente ou alheio continua com o caso de uso, que é quem sabe se o recurso existe.
 */
@Service
public class PoliticaDeAcesso {

    /** Título que substitui o de um documento sigiloso para quem não o acessa (o nome do arquivo e a extensão somem). */
    public static final String TITULO_SIGILOSO = "Documento sigiloso";

    private final UsuarioAtual usuarioAtual;

    public PoliticaDeAcesso(UsuarioAtual usuarioAtual) {
        this.usuarioAtual = usuarioAtual;
    }

    // --- regras puras

    /** RN07: o Operador só acessa o que tem o próprio CPF/CNPJ como interessado; Gestor e Administrador, tudo. */
    public boolean podeAcessarRequerimento(Usuario usuario, String cpfCnpjDoInteressado) {
        return RestricaoDeInteressado.de(usuario).permite(cpfCnpjDoInteressado);
    }

    /** RF20: só Gestor e Administrador visualizam, selecionam e incluem no dossiê documentos sigilosos. */
    public boolean podeVerDocumentoSigiloso(Usuario usuario) {
        return usuario.perfil().podeVerSigilosos();
    }

    public boolean podeAcessarDocumento(Usuario usuario, boolean sigiloso) {
        return !sigiloso || podeVerDocumentoSigiloso(usuario);
    }

    /** RN12: o nome do responsável pela tarefa atual é visível só para Gestor e Administrador. */
    public boolean podeVerResponsavelTarefa(Usuario usuario) {
        return usuario.perfil().podeVerResponsavelTarefa();
    }

    /**
     * Pedido de dossiê: quem o criou, enquanto ainda tiver acesso ao requerimento (RN07), e quem acessa qualquer
     * requerimento e qualquer sigiloso (Gestor e Administrador), porque o pedido de outro usuário pode conter
     * documentos sigilosos. Para os demais o pedido não existe (o caso de uso responde 404).
     */
    public boolean podeAcessarPedido(Usuario usuario, PedidoDeUsuario pedido) {
        if (usuario.login().equals(pedido.loginDoCriador())) {
            return podeAcessarRequerimento(usuario, pedido.cpfCnpjDoInteressado());
        }
        return podeVerPedidosDeOutros(usuario);
    }

    /**
     * Ver pedidos de dossiê de outros usuários e quem os gerou ("Dossiês gerados", S4): quem acessa qualquer requerimento
     * e qualquer sigiloso (Gestor e Administrador).
     */
    public boolean podeVerPedidosDeOutros(Usuario usuario) {
        return !usuario.perfil().restritoAoInteressado() && podeVerDocumentoSigiloso(usuario);
    }

    /** O título como sai para o usuário: o de um documento que ele não acessa é mascarado. */
    public static String mascararTitulo(boolean acessivel, String titulo) {
        return acessivel ? titulo : TITULO_SIGILOSO;
    }

    /** Nome do arquivo, extensão e demais dados que identificam o arquivo: omitidos quando o usuário não o acessa. */
    public static String mascararDadoDoArquivo(boolean acessivel, String valor) {
        return acessivel ? valor : null;
    }

    // --- usuário da requisição

    /** Para buscas em lote: a restrição vai para a consulta, e o total já vem filtrado. */
    public RestricaoDeInteressado restricaoDoUsuarioAtual() {
        return RestricaoDeInteressado.de(usuarioAtual.obter());
    }

    /** 403 {@code NAO_INTERESSADO} se o usuário da requisição não pode acessar o requerimento (RN07). */
    public void exigirAcessoAoRequerimento(String cpfCnpjDoInteressado) {
        if (!podeAcessarRequerimento(usuarioAtual.obter(), cpfCnpjDoInteressado)) {
            throw new NaoInteressadoException();
        }
    }

    public boolean podeVerDocumentoSigiloso() {
        return podeVerDocumentoSigiloso(usuarioAtual.obter());
    }

    public boolean podeAcessarDocumento(boolean sigiloso) {
        return podeAcessarDocumento(usuarioAtual.obter(), sigiloso);
    }

    /** 403 {@code DOCUMENTO_SIGILOSO} se o documento é sigiloso e o perfil não o acessa (RF20). */
    public void exigirAcessoAoDocumento(boolean sigiloso) {
        if (!podeAcessarDocumento(sigiloso)) {
            throw new DocumentoSigilosoException();
        }
    }

    public boolean podeVerResponsavelTarefa() {
        return podeVerResponsavelTarefa(usuarioAtual.obter());
    }

    public boolean podeVerPedidosDeOutros() {
        return podeVerPedidosDeOutros(usuarioAtual.obter());
    }

    public String loginDoUsuarioAtual() {
        return usuarioAtual.obter().login();
    }

    public boolean podeAcessarPedido(PedidoDeUsuario pedido) {
        return podeAcessarPedido(usuarioAtual.obter(), pedido);
    }
}
