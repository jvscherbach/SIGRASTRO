package br.gov.mt.sema.rastro.financeiro.infrastructure.tramitacao;

import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.rastro.financeiro.application.port.PassagensDeIsencao;
import br.gov.mt.sema.rastro.tramitacao.application.LerTramitacao;
import br.gov.mt.sema.rastro.tramitacao.domain.MotivosDaVolta;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Liga a porta {@link PassagensDeIsencao} ao caso de uso publicado da tramitação ({@link LerTramitacao}, com cache, tempo
 * total e limite de leituras do Camunda). Só lê as passagens da etapa {@code isencao_confirmada} do caminho exato; o plano
 * B é aproximado e não serve de data. Falha da tramitação vira lista vazia: o financeiro segue completo por conta própria.
 */
@Component
class PassagensDeIsencaoPelaTramitacao implements PassagensDeIsencao {

    private static final Logger log = LoggerFactory.getLogger(PassagensDeIsencaoPelaTramitacao.class);

    private final LerTramitacao tramitacao;

    PassagensDeIsencaoPelaTramitacao(LerTramitacao tramitacao) {
        this.tramitacao = tramitacao;
    }

    @Override
    public List<LocalDateTime> entradas(long idRequerimento) {
        try {
            TramitacaoDoRequerimento lida = tramitacao.ler(idRequerimento);
            if (lida.aproximado() || lida.estado() == TramitacaoDoRequerimento.Estado.INDISPONIVEL
                    || lida.estado() == TramitacaoDoRequerimento.Estado.NAO_SUPORTADO) {
                return List.of();
            }
            return lida.caminho().passagens().stream()
                    .filter(p -> p.idDaEtapa().map(MotivosDaVolta.ISENCAO::equals).orElse(false))
                    .map(PassagemPelaEtapa::entrada)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Tramitação do requerimento {} sem passagens de isenção ({})", idRequerimento,
                    e.getClass().getSimpleName());
            return List.of();
        }
    }
}
