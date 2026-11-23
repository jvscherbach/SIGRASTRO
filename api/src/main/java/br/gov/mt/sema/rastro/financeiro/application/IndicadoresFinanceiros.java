package br.gov.mt.sema.rastro.financeiro.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import br.gov.mt.sema.rastro.financeiro.application.port.LancamentosDoRequerimento;
import br.gov.mt.sema.rastro.financeiro.domain.ResumoFinanceiro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Indicador financeiro opcional da lista da busca (S9, bloco 5): só o resumo de cada requerimento, numa consulta em lote a
 * cada {@value LancamentosDoRequerimento#MAXIMO_POR_LOTE} (nunca uma por linha) e sem ler a tramitação. Recebe só ids que
 * o usuário já pode ver (a lista da busca aplica o RN07 na própria consulta); a rota HTTP e o limite de pedidos
 * simultâneos da tela (o da S7, {@code RASTRO_POSICOES_SIMULTANEAS}) são dos blocos 6 e 8.
 *
 * <p>Falha do banco: mapa vazio (a coluna fica sem indicador), nunca erro na busca.
 */
@Service
public class IndicadoresFinanceiros {

    private static final Logger log = LoggerFactory.getLogger(IndicadoresFinanceiros.class);

    private final LerLancamentos lancamentos;

    public IndicadoresFinanceiros(LerLancamentos lancamentos) {
        this.lancamentos = lancamentos;
    }

    public Map<Long, ResumoFinanceiro> resumos(Collection<Long> idsAutorizados) {
        if (idsAutorizados.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, ResumoFinanceiro> resumos = new LinkedHashMap<>();
            lancamentos.historicoEmLote(idsAutorizados).forEach((id, h) -> resumos.put(id, h.resumo()));
            return resumos;
        } catch (RuntimeException e) {
            log.warn("Indicador financeiro indisponível ({} requerimentos; {})", idsAutorizados.size(),
                    e.getClass().getSimpleName());
            return Map.of();
        }
    }
}
