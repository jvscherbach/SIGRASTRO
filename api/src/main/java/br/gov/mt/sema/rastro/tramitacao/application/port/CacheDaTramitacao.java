package br.gov.mt.sema.rastro.tramitacao.application.port;

import java.time.Duration;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;

/**
 * Cache curto da tramitação, em memória (S8, bloco 7), para proteger o Camunda. Como o da posição: a chave é só o id do
 * requerimento e a versão do catálogo de etapas (nunca CPF nem usuário); o valor guardado já vem sem CPF e <b>com</b> os
 * nomes (o RN12 e o parâmetro de nome são aplicados na leitura, depois do cache). {@code INDISPONIVEL} nunca entra.
 */
public interface CacheDaTramitacao {

    Optional<TramitacaoDoRequerimento> obter(long idRequerimento, String versaoDoCatalogo);

    void guardar(long idRequerimento, String versaoDoCatalogo, TramitacaoDoRequerimento tramitacao, Duration validade);
}
