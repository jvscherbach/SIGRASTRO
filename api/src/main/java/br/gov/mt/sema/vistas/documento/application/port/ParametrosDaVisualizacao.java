package br.gov.mt.sema.vistas.documento.application.port;

import java.nio.file.Path;

/**
 * Parâmetros do conteúdo para o modal (S3). No protótipo vêm de variáveis de ambiente (implementação em
 * {@code config}); a conversão em si usa os mesmos {@link ParametrosDaConversao} do dossiê.
 */
public interface ParametrosDaVisualizacao {

    /** Tempo de vida de um arquivo no cache, em minutos. */
    int ttlMinutos();

    /** Teto de espaço do cache, em MB; acima, os mais antigos são descartados. */
    long cacheMaxMb();

    /** Conversões do modal em paralelo. */
    int conversoesSimultaneas();

    /** Conversões do modal que esperam a vez; com a fila cheia, 429. */
    int filaDeConversoes();

    /** Diretório do cache (subdiretório de {@code VISTAS_DOSSIE_DIRETORIO}). */
    Path diretorio();
}
