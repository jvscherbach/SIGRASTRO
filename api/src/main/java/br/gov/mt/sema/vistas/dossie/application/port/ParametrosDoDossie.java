package br.gov.mt.sema.vistas.dossie.application.port;

import java.nio.file.Path;

/**
 * Parâmetros do dossiê. No protótipo vêm de variáveis de ambiente (implementação em {@code config}); a S5 troca a
 * implementação por parâmetros administráveis sem mudar quem os consome.
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

    boolean sumario();

    boolean marcadores();

    boolean numeracao();

    /** Diretório temporário; cada pedido usa uma subpasta própria. */
    Path diretorio();
}
