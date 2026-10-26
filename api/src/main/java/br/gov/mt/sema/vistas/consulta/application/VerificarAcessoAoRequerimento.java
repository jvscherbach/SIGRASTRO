package br.gov.mt.sema.vistas.consulta.application;

import br.gov.mt.sema.vistas.acesso.application.ObterRestricaoDeInteressado;
import br.gov.mt.sema.vistas.consulta.domain.Requerimento;
import br.gov.mt.sema.vistas.shared.application.exception.NaoInteressadoException;
import br.gov.mt.sema.vistas.shared.application.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;

/**
 * Contrato do módulo consulta para os demais: o requerimento existe (404) e o usuário da requisição pode acessá-lo
 * (RN07, 403 NAO_INTERESSADO). Devolve só o requerimento, sem objeto, prioridade nem tarefa.
 */
@Service
public class VerificarAcessoAoRequerimento {

    private final RequerimentosDoSiga siga;
    private final ObterRestricaoDeInteressado restricaoDoUsuario;

    public VerificarAcessoAoRequerimento(RequerimentosDoSiga siga, ObterRestricaoDeInteressado restricaoDoUsuario) {
        this.siga = siga;
        this.restricaoDoUsuario = restricaoDoUsuario;
    }

    public Requerimento executar(long id) {
        Requerimento requerimento = siga.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Requerimento não encontrado."));
        if (!restricaoDoUsuario.paraUsuarioAtual().permite(requerimento.cpfCnpjDoInteressado())) {
            throw new NaoInteressadoException();
        }
        return requerimento;
    }
}
