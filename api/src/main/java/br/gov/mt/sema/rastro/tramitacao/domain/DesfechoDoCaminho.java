package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Como o caminho termina (S8, bloco 4; docs/15, "Finais e divergências"). A <b>bola preta</b> do fluxograma só existe com
 * o evento de fim ({@link #fim()}); o rótulo vem sempre do evento, nunca da situação do Licenciamento.
 *
 * @param estado  em andamento, concluído (instância ativa na verificação da revisão, regra técnica 9: sem bola preta) ou
 *                encerrado (com o final do catálogo, ou sem ele se o evento de fim não está mapeado)
 * @param fim     o final do catálogo pelo {@code END_ACT_ID_}; só no estado encerrado
 * @param quando  encerrado: fim da instância; concluído: entrada na verificação da revisão (fim da publicação)
 * @param avisos  divergências do final e da situação, em ordem
 */
public record DesfechoDoCaminho(Estado estado, Optional<FinalDoFluxo> fim, Optional<LocalDateTime> quando,
        List<Aviso> avisos) {

    public DesfechoDoCaminho {
        Objects.requireNonNull(estado, "estado");
        fim = Objects.requireNonNullElse(fim, Optional.empty());
        quando = Objects.requireNonNullElse(quando, Optional.empty());
        avisos = List.copyOf(Objects.requireNonNullElse(avisos, List.of()));
    }

    public enum Estado { SEM_INSTANCIA, EM_ANDAMENTO, CONCLUIDO, ENCERRADO }

    /** Desenhar a bola preta com o rótulo em cima: só com o evento de fim mapeado. */
    public boolean bolaPreta() {
        return fim.isPresent();
    }

    /** Rótulo do desfecho: o do final, ou "Concluído" no arquivamento; vazio em andamento. */
    public Optional<String> rotulo() {
        if (fim.isPresent()) {
            return fim.map(FinalDoFluxo::rotulo);
        }
        return estado == Estado.CONCLUIDO ? Optional.of(AvaliarDesfecho.ROTULO_CONCLUIDO) : Optional.empty();
    }
}
