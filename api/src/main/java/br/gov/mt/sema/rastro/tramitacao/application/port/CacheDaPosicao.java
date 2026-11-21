package br.gov.mt.sema.rastro.tramitacao.application.port;

import java.time.Duration;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;

/**
 * Cache curto da posição, em memória, para proteger o Camunda. A chave é só o id do requerimento e a versão do catálogo:
 * nunca CPF nem usuário. O valor guardado já vem sem os {@code ASSIGNEE_} (a resolução do responsável os remove).
 */
public interface CacheDaPosicao {

    Optional<PosicaoAtual> obter(long idRequerimento, String versaoDoCatalogo);

    void guardar(long idRequerimento, String versaoDoCatalogo, PosicaoAtual posicao, Duration validade);
}
