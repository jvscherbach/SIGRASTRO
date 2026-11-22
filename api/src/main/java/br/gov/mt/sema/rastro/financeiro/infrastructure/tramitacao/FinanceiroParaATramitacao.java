package br.gov.mt.sema.rastro.financeiro.infrastructure.tramitacao;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.financeiro.application.LerLancamentos;
import br.gov.mt.sema.rastro.financeiro.domain.CaminhoDoFluxo;
import br.gov.mt.sema.rastro.financeiro.domain.HistoricoFinanceiro;
import br.gov.mt.sema.rastro.financeiro.domain.LancamentoAvaliado;
import br.gov.mt.sema.rastro.financeiro.domain.MarcarPassagens;
import br.gov.mt.sema.rastro.financeiro.domain.ResumoFinanceiro;
import br.gov.mt.sema.rastro.tramitacao.application.port.FinanceiroDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementa a porta {@link FinanceiroDoRequerimento} da tramitação (S9, bloco 5) com o financeiro: o aviso de taxa
 * vencida (resumo {@code VENCIDO}, em lote) e as marcas nas passagens ({@link MarcarPassagens}, com o caminho que a
 * própria tramitação entregou, sem lê-la de novo). Qualquer falha do financeiro (banco fora, SIGA não configurado) vira
 * "nada a acrescentar": a posição e a tramitação saem como sem o financeiro.
 */
@Component
class FinanceiroParaATramitacao implements FinanceiroDoRequerimento {

    private static final Logger log = LoggerFactory.getLogger(FinanceiroParaATramitacao.class);

    private final LerLancamentos lancamentos;

    FinanceiroParaATramitacao(LerLancamentos lancamentos) {
        this.lancamentos = lancamentos;
    }

    @Override
    public Map<Long, TaxaVencida> taxasVencidas(Collection<Long> idsRequerimento) {
        if (idsRequerimento.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, TaxaVencida> vencidas = new LinkedHashMap<>();
            lancamentos.historicoEmLote(idsRequerimento).forEach((id, h) -> {
                if (h.resumo() == ResumoFinanceiro.VENCIDO) {
                    vencidas.put(id, vencida(h));
                }
            });
            return vencidas;
        } catch (RuntimeException e) {
            log.warn("Financeiro indisponível para o aviso de taxa vencida ({} requerimentos; {})", idsRequerimento.size(),
                    e.getClass().getSimpleName());
            return Map.of();
        }
    }

    /** A taxa vencida mais recente (a última da lista, por emissão). */
    private static TaxaVencida vencida(HistoricoFinanceiro h) {
        LancamentoAvaliado ultima = h.lancamentos().stream().filter(LancamentoAvaliado::vencida).toList().getLast();
        return new TaxaVencida(ultima.venceEm(), ultima.vencidaPorData());
    }

    @Override
    public List<List<MarcaFinanceira>> marcas(long idRequerimento, List<PassagemPelaEtapa> passagens) {
        if (passagens.isEmpty()) {
            return List.of();
        }
        try {
            CaminhoDoFluxo caminho = CaminhoPelaTramitacao.converter(passagens);
            List<List<br.gov.mt.sema.rastro.financeiro.domain.MarcaFinanceira>> marcas =
                    MarcarPassagens.marcar(lancamentos.historico(idRequerimento, caminho), caminho);
            return marcas.stream().map(daPassagem -> daPassagem.stream().map(FinanceiroParaATramitacao::paraATela)
                    .toList()).toList();
        } catch (RuntimeException e) {
            log.warn("Financeiro indisponível para as marcas da tramitação do requerimento {} ({})", idRequerimento,
                    e.getClass().getSimpleName());
            return NENHUM.marcas(idRequerimento, passagens);
        }
    }

    private static MarcaFinanceira paraATela(br.gov.mt.sema.rastro.financeiro.domain.MarcaFinanceira m) {
        return new MarcaFinanceira(m.tipo().name(), m.tipo().rotulo(), m.data(), m.valor(), m.dataAConfirmar());
    }
}
