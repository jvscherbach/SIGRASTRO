package br.gov.mt.sema.rastro.auditoria.application;

import java.util.function.Supplier;

import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.auditoria.application.port.RequisicaoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.UsuarioDoEvento;
import org.springframework.stereotype.Service;

/**
 * Retrato da requisição corrente para os eventos: usuário, origem e correlação. Como {@link RegistrarEvento}, nunca
 * lança; o que não puder ser lido fica nulo, e {@code registrar} descarta o evento sem usuário. Na thread de geração não
 * há requisição: quem precisa do retrato o guarda antes (pedido de dossiê, ticket).
 */
@Service
public class ContextoDoEvento {

    private final UsuarioAtual usuarioAtual;
    private final RequisicaoDoEvento requisicao;

    public ContextoDoEvento(UsuarioAtual usuarioAtual, RequisicaoDoEvento requisicao) {
        this.usuarioAtual = usuarioAtual;
        this.requisicao = requisicao;
    }

    public UsuarioDoEvento usuario() {
        return seguro(() -> usuarioAtual.talvez()
                .map(u -> new UsuarioDoEvento(u.login(), u.nome(), u.rotuloDoPerfil()))
                .orElse(null));
    }

    public String origem() {
        return seguro(requisicao::origem);
    }

    public String correlationId() {
        return seguro(requisicao::correlationId);
    }

    /** Evento em nome do usuário da requisição. */
    public NovoEvento.Construtor novo(TipoDeEvento tipo) {
        return novo(tipo, usuario());
    }

    /** Evento em nome de outro usuário (ticket de download, sem sessão), com a origem e a correlação da requisição. */
    public NovoEvento.Construtor novo(TipoDeEvento tipo, UsuarioDoEvento usuario) {
        return NovoEvento.de(tipo, usuario).origem(origem()).correlacao(correlationId());
    }

    private static <T> T seguro(Supplier<T> leitura) {
        try {
            return leitura.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
