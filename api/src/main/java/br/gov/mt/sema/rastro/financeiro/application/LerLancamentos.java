package br.gov.mt.sema.rastro.financeiro.application;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.domain.AvaliarSituacao;
import br.gov.mt.sema.rastro.financeiro.domain.ClassificarValidade;
import br.gov.mt.sema.rastro.financeiro.domain.HistoricoFinanceiro;
import br.gov.mt.sema.rastro.financeiro.domain.LancamentosClassificados;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import org.springframework.stereotype.Service;

/**
 * Lê os lançamentos de taxa de um requerimento (ou de uma lista, em lote), aplica a regra de validade (S9, bloco 2) e
 * avalia situação, vencimento por data e ciclos (bloco 3) com o relógio do projeto. Não decide acesso nem trata falha do
 * banco: quem expõe (bloco 6) verifica a regra de consulta antes e transforma a falha em {@code INDISPONIVEL}. Não depende
 * da tramitação: funciona com o Camunda fora do ar e sem instância.
 */
@Service
public class LerLancamentos {

    private final LancamentosDoRequerimento lancamentos;
    private final Clock relogio;

    public LerLancamentos(LancamentosDoRequerimento lancamentos, Clock relogio) {
        this.lancamentos = lancamentos;
        this.relogio = relogio;
    }

    public LancamentosClassificados doRequerimento(long idRequerimento) {
        return ClassificarValidade.classificar(lancamentos.lerDoRequerimento(idRequerimento));
    }

    /** Uma consulta a cada {@value LancamentosDoRequerimento#MAXIMO_POR_LOTE} requerimentos; todo id pedido volta no mapa. */
    public Map<Long, LancamentosClassificados> emLote(Collection<Long> idsRequerimento) {
        Map<Long, LancamentosClassificados> resultado = new LinkedHashMap<>();
        Map<Long, LeituraDosLancamentos> lidos = lancamentos.lerEmLote(idsRequerimento);
        idsRequerimento.forEach(id -> resultado.computeIfAbsent(id,
                k -> ClassificarValidade.classificar(lidos.getOrDefault(k, LeituraDosLancamentos.NENHUM))));
        return resultado;
    }

    /** Histórico avaliado de um requerimento (uma consulta), com "hoje" em Cuiabá pelo relógio do projeto. */
    public HistoricoFinanceiro historico(long idRequerimento) {
        return AvaliarSituacao.avaliar(doRequerimento(idRequerimento), relogio);
    }

    /** Histórico avaliado em lote (uma consulta a cada 500 requerimentos), com o mesmo "hoje" para todos. */
    public Map<Long, HistoricoFinanceiro> historicoEmLote(Collection<Long> idsRequerimento) {
        Map<Long, HistoricoFinanceiro> resultado = new LinkedHashMap<>();
        emLote(idsRequerimento).forEach((id, c) -> resultado.put(id, AvaliarSituacao.avaliar(c, relogio)));
        return resultado;
    }
}
