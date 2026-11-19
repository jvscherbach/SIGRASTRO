package br.gov.mt.sema.rastro.integration.licenciamento;

import java.time.Duration;

/**
 * Limites de tempo de um download (docs/05): conexão, inatividade da leitura (tempo sem receber bytes) e teto total
 * por documento, somando as novas tentativas.
 */
record LimitesDeDownload(Duration conexao, Duration inatividade, Duration totalMaximo) {

    static final Duration CONEXAO_PADRAO = Duration.ofSeconds(10);
}
