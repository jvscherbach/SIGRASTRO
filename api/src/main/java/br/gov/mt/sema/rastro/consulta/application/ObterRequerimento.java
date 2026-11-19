package br.gov.mt.sema.rastro.consulta.application;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.TarefaAtual;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ObterRequerimento {

    private static final Logger log = LoggerFactory.getLogger(ObterRequerimento.class);

    private final RequerimentosDoSiga siga;
    private final ObjetoEPrioridadeDoRequerimento objetoEPrioridade;
    private final TarefaAtualDoRequerimento tarefas;
    private final PoliticaDeAcesso politica;

    public ObterRequerimento(RequerimentosDoSiga siga, ObjetoEPrioridadeDoRequerimento objetoEPrioridade,
            TarefaAtualDoRequerimento tarefas, PoliticaDeAcesso politica) {
        this.siga = siga;
        this.objetoEPrioridade = objetoEPrioridade;
        this.tarefas = tarefas;
        this.politica = politica;
    }

    public DetalheDoRequerimento executar(long id) {
        Requerimento requerimento = siga.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Requerimento não encontrado."));
        politica.exigirAcessoAoRequerimento(requerimento.cpfCnpjDoInteressado());
        // Objeto e prioridade fazem parte do detalhe: se falharem, o detalhe inteiro falha (503 ou 500).
        return new DetalheDoRequerimento(requerimento, objetoEPrioridade.objeto(id),
                objetoEPrioridade.prioridade(id), tarefaAtual(id));
    }

    /**
     * A tarefa é informação complementar: falha ao lê-la não derruba o detalhe. O responsável só sai para quem o
     * perfil permite; para os demais é removido aqui, antes de chegar à API.
     */
    private TarefaAtual tarefaAtual(long idRequerimento) {
        TarefaAtual tarefa;
        try {
            tarefa = tarefas.obter(idRequerimento);
        } catch (RuntimeException e) {
            log.warn("Tarefa atual indisponível para o requerimento {}", idRequerimento, e);
            return TarefaAtual.indisponivel();
        }
        return politica.podeVerResponsavelTarefa() ? tarefa : tarefa.semResponsavel();
    }
}
