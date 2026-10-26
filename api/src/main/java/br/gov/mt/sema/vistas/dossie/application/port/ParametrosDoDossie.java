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

    int downloadTimeoutSegundos();

    boolean sumario();

    boolean marcadores();

    boolean numeracao();

    /** Diretório temporário; cada pedido usa uma subpasta própria. */
    Path diretorio();
}
