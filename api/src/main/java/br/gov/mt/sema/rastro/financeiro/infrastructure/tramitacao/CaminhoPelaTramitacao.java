package br.gov.mt.sema.rastro.financeiro.infrastructure.tramitacao;

import java.util.List;

import br.gov.mt.sema.rastro.financeiro.application.port.CaminhoDaTramitacao;
import br.gov.mt.sema.rastro.financeiro.domain.CaminhoDoFluxo;
import br.gov.mt.sema.rastro.financeiro.domain.PassagemDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.application.LerTramitacao;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Liga a porta {@link CaminhoDaTramitacao} ao caso de uso publicado da tramitação ({@link LerTramitacao}, com cache, tempo
 * total e limite de leituras do Camunda). Só o caminho exato serve: o plano B é aproximado e não dá data nem ramo. Falha
 * da tramitação vira {@link CaminhoDoFluxo#NENHUM}: o financeiro segue completo por conta própria.
 */
@Component
class CaminhoPelaTramitacao implements CaminhoDaTramitacao {

    private static final Logger log = LoggerFactory.getLogger(CaminhoPelaTramitacao.class);

    private final LerTramitacao tramitacao;

    CaminhoPelaTramitacao(LerTramitacao tramitacao) {
        this.tramitacao = tramitacao;
    }

    @Override
    public CaminhoDoFluxo ler(long idRequerimento) {
        try {
            TramitacaoDoRequerimento lida = tramitacao.ler(idRequerimento);
            if (lida.aproximado() || lida.estado() == TramitacaoDoRequerimento.Estado.INDISPONIVEL
                    || lida.estado() == TramitacaoDoRequerimento.Estado.NAO_SUPORTADO) {
                return CaminhoDoFluxo.NENHUM;
            }
            return converter(lida.caminho().passagens());
        } catch (RuntimeException e) {
            log.warn("Tramitação do requerimento {} sem caminho para o financeiro ({})", idRequerimento,
                    e.getClass().getSimpleName());
            return CaminhoDoFluxo.NENHUM;
        }
    }

    /** As passagens da tramitação na forma mínima do financeiro, na mesma ordem (índices alinhados). */
    static CaminhoDoFluxo converter(List<PassagemPelaEtapa> passagens) {
        return new CaminhoDoFluxo(passagens.stream()
                .map(p -> new PassagemDoFluxo(p.idDaEtapa(), p.ramo() == Ramo.CORRECAO, p.entrada()))
                .toList());
    }
}
