package br.gov.mt.sema.rastro.financeiro.application;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import br.gov.mt.sema.rastro.financeiro.application.port.CaminhoDaTramitacao;
import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.domain.AvaliarSituacao;
import br.gov.mt.sema.rastro.financeiro.domain.CaminhoDoFluxo;
import br.gov.mt.sema.rastro.financeiro.domain.ClassificarValidade;
import br.gov.mt.sema.rastro.financeiro.domain.HistoricoFinanceiro;
import br.gov.mt.sema.rastro.financeiro.domain.LancamentosClassificados;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import org.springframework.stereotype.Service;

/**
 * Lê os lançamentos de taxa de um requerimento (ou de uma lista, em lote), aplica a regra de validade (S9, bloco 2) e
 * avalia situação, vencimento por data e ciclos (bloco 3) com o relógio do projeto; com o caminho exato da tramitação,
 * a etiqueta "da correção" e a data de apoio da isenção (blocos 4 e 5). Não decide acesso nem trata falha do banco: quem
 * expõe (bloco 6) verifica a regra de consulta antes e transforma a falha em {@code INDISPONIVEL}. Funciona com o Camunda
 * fora do ar e sem instância (perde só o que vem do caminho).
 */
@Service
public class LerLancamentos {

    private final LancamentosDoRequerimento lancamentos;
    private final Clock relogio;
    private final CaminhoDaTramitacao caminhos;

    public LerLancamentos(LancamentosDoRequerimento lancamentos, Clock relogio, CaminhoDaTramitacao caminhos) {
        this.lancamentos = lancamentos;
        this.relogio = relogio;
        this.caminhos = caminhos;
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

    /**
     * Histórico avaliado de um requerimento (uma consulta), com "hoje" em Cuiabá pelo relógio do projeto. A tramitação só é
     * lida quando muda alguma coisa ({@link AvaliarSituacao#precisaDoCaminho}): há guia (pode ser "da correção") ou
     * isenção sem {@code DATA_EMISSAO}.
     */
    public HistoricoFinanceiro historico(long idRequerimento) {
        LancamentosClassificados classificados = doRequerimento(idRequerimento);
        HistoricoFinanceiro semCaminho = AvaliarSituacao.avaliar(classificados, relogio);
        if (!AvaliarSituacao.precisaDoCaminho(semCaminho)) {
            return semCaminho;
        }
        return AvaliarSituacao.avaliar(classificados, relogio, caminhos.ler(idRequerimento));
    }

    /** Histórico com um caminho já em mãos (a tramitação que pede as marcas, bloco 5): não lê a tramitação de novo. */
    public HistoricoFinanceiro historico(long idRequerimento, CaminhoDoFluxo caminho) {
        return AvaliarSituacao.avaliar(doRequerimento(idRequerimento), relogio, caminho);
    }

    /**
     * Histórico avaliado em lote (uma consulta a cada 500 requerimentos), com o mesmo "hoje" para todos. Sem a tramitação
     * (lista da busca e lista do processo: nada de uma leitura do Camunda por linha): sem "da correção" e a data da isenção
     * só do lançamento.
     */
    public Map<Long, HistoricoFinanceiro> historicoEmLote(Collection<Long> idsRequerimento) {
        Map<Long, HistoricoFinanceiro> resultado = new LinkedHashMap<>();
        emLote(idsRequerimento).forEach((id, c) -> resultado.put(id, AvaliarSituacao.avaliar(c, relogio)));
        return resultado;
    }
}
