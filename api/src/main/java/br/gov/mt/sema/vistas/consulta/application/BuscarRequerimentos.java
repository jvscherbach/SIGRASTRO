package br.gov.mt.sema.vistas.consulta.application;

import java.util.List;

import br.gov.mt.sema.vistas.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.vistas.acesso.domain.RestricaoDeInteressado;
import br.gov.mt.sema.vistas.consulta.domain.CriterioBusca;
import br.gov.mt.sema.vistas.consulta.domain.Pagina;
import br.gov.mt.sema.vistas.consulta.domain.Requerimento;
import br.gov.mt.sema.vistas.consulta.domain.ResumoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.TipoBusca;
import br.gov.mt.sema.vistas.shared.application.exception.NaoInteressadoException;
import br.gov.mt.sema.vistas.shared.application.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;

@Service
public class BuscarRequerimentos {

    private final RequerimentosDoSiga siga;
    private final PoliticaDeAcesso politica;

    public BuscarRequerimentos(RequerimentosDoSiga siga, PoliticaDeAcesso politica) {
        this.siga = siga;
        this.politica = politica;
    }

    public Pagina<ResumoRequerimento> executar(PedidoDeBusca pedido) {
        CriterioBusca criterio = pedido.paraCriterio();
        RestricaoDeInteressado restricao = politica.restricaoDoUsuarioAtual();

        if (criterio.apenasNumero()) {
            return criterio.tipo() == TipoBusca.PROCESSO
                    ? buscarPorNumeroDeProcesso(criterio, restricao)
                    : buscarPorNumeroDeRequerimento(criterio);
        }
        if (restricao.semAcessoAlgum()) {
            return Pagina.vazia(criterio.pagina(), criterio.tamanho());
        }
        // A restrição (RN07) vai para a consulta: total e paginação já vêm filtrados.
        return siga.buscarPorCriterios(restricao.restrita() ? criterio.comRestricao(restricao.digitos()) : criterio);
    }

    /** O número do requerimento é exato: 1 resultado ou 404. */
    private Pagina<ResumoRequerimento> buscarPorNumeroDeRequerimento(CriterioBusca criterio) {
        Requerimento requerimento = siga.buscarPorNumeroDeRequerimento(criterio.numero())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Requerimento " + criterio.numero().texto() + " não encontrado."));
        politica.exigirAcessoAoRequerimento(requerimento.cpfCnpjDoInteressado());
        return new Pagina<>(List.of(requerimento.resumo()), 1, 0, criterio.tamanho());
    }

    /**
     * O número do processo pode repetir (1:N): devolve todos os requerimentos daquele número, já com a restrição do
     * interessado (RN07) aplicada na consulta. Vazio para o Operador significa 403 se o número existe para outros
     * interessados, e 404 se não existe.
     */
    private Pagina<ResumoRequerimento> buscarPorNumeroDeProcesso(CriterioBusca criterio,
            RestricaoDeInteressado restricao) {
        if (!restricao.semAcessoAlgum()) {
            Pagina<ResumoRequerimento> pagina = siga.buscarPorCriterios(
                    restricao.restrita() ? criterio.comRestricao(restricao.digitos()) : criterio);
            if (pagina.total() > 0) {
                return pagina;
            }
        }
        if (restricao.restrita() && siga.buscarPorCriterios(criterio.soParaVerificarExistencia()).total() > 0) {
            throw new NaoInteressadoException();
        }
        throw new RecursoNaoEncontradoException("Processo " + criterio.numero().texto() + " não encontrado.");
    }
}
