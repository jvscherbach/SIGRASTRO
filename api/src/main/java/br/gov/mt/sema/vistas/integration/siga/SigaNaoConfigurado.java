package br.gov.mt.sema.vistas.integration.siga;

import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.vistas.consulta.application.ObjetoEPrioridadeDoRequerimento;
import br.gov.mt.sema.vistas.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.vistas.consulta.application.TarefaAtualDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.CriterioBusca;
import br.gov.mt.sema.vistas.consulta.domain.NumeroRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.Pagina;
import br.gov.mt.sema.vistas.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.vistas.consulta.domain.Requerimento;
import br.gov.mt.sema.vistas.consulta.domain.ResumoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.TarefaAtual;
import br.gov.mt.sema.vistas.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.shared.application.exception.SigaIndisponivelException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Sem SIGA_DB_URL a aplicação sobe normalmente e toda consulta responde 503 SIGA_INDISPONIVEL. */
@Component
@ConditionalOnExpression(SigaCondicoes.NAO_CONFIGURADO)
class SigaNaoConfigurado implements RequerimentosDoSiga, TarefaAtualDoRequerimento, ObjetoEPrioridadeDoRequerimento,
        DocumentosDoRequerimento {

    @Override
    public Optional<Requerimento> buscarPorNumeroDeRequerimento(NumeroRequerimento numero) {
        throw new SigaIndisponivelException();
    }

    @Override
    public Optional<Requerimento> buscarPorId(long id) {
        throw new SigaIndisponivelException();
    }

    @Override
    public Pagina<ResumoRequerimento> buscarPorCriterios(CriterioBusca criterio) {
        throw new SigaIndisponivelException();
    }

    @Override
    public TarefaAtual obter(long idRequerimento) {
        throw new SigaIndisponivelException();
    }

    @Override
    public ObjetoDoRequerimento objeto(long idRequerimento) {
        throw new SigaIndisponivelException();
    }

    @Override
    public PrioridadeAtendimento prioridade(long idRequerimento) {
        throw new SigaIndisponivelException();
    }

    @Override
    public List<Documento> listar(long idRequerimento) {
        throw new SigaIndisponivelException();
    }
}
