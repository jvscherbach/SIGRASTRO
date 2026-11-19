package br.gov.mt.sema.rastro.dossie.application.port;

import java.nio.file.Path;

/**
 * Parâmetros do dossiê: variáveis de ambiente com as sobreposições da tela de configurações (implementação em
 * {@code config}).
 */
public interface ParametrosDoDossie {

    int ttlMinutos();

    /** Gerações simultâneas. */
    int threads();

    /** Tempo máximo de inatividade na leitura do arquivo (sem receber bytes), em segundos. */
    int downloadTimeoutSegundos();

    /** Teto total para obter um documento, somando as novas tentativas, em segundos. */
    int downloadTotalMaximoSegundos();

    /** Espaço livre mínimo no diretório do pedido antes de cada download, em MB. */
    long espacoMinimoMb();

    /** Acima deste tamanho (MB) o front baixa por ticket, em streaming, sem Blob nem pré-visualização (D49). */
    default long blobMaxMb() {
        return 150;
    }

    /**
     * Horas que um pedido encerrado (expirado, baixado ou cancelado) continua na lista "Dossiês gerados" depois do
     * vencimento, sem arquivo ({@code DOSSIE_HISTORICO_HORAS}, S4). Histórico em memória: some num reinício (D09).
     */
    default int historicoHoras() {
        return 24;
    }

    boolean sumario();

    boolean marcadores();

    boolean numeracao();

    /** Diretório temporário; cada pedido usa uma subpasta própria. */
    Path diretorio();
}
