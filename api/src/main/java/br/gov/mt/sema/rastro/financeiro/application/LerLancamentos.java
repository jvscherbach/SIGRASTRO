package br.gov.mt.sema.rastro.financeiro.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.domain.ClassificarValidade;
import br.gov.mt.sema.rastro.financeiro.domain.LancamentosClassificados;
import br.gov.mt.sema.rastro.financeiro.domain.LeituraDosLancamentos;
import org.springframework.stereotype.Service;

/**
 * Lê os lançamentos de taxa de um requerimento (ou de uma lista, em lote) e aplica a regra de validade (S9, bloco 2). Não
 * decide acesso nem trata falha do banco: quem expõe (bloco 6) verifica a regra de consulta antes e transforma a falha em
 * {@code INDISPONIVEL}. Não depende da tramitação: funciona com o Camunda fora do ar e sem instância.
 */
@Service
public class LerLancamentos {

    private final LancamentosDoRequerimento lancamentos;

    public LerLancamentos(LancamentosDoRequerimento lancamentos) {
        this.lancamentos = lancamentos;
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
}
