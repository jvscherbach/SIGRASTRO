package br.gov.mt.sema.rastro.documento.application.port;

import java.nio.file.Path;

/**
 * Parâmetros do download das geometrias em ZIP (RN10, S4). No protótipo vêm de variáveis de ambiente (implementação em
 * {@code config}); a obtenção de cada arquivo usa o mesmo cliente e os mesmos tempos do dossiê.
 */
public interface ParametrosDasGeometrias {

    /** Tamanho máximo somado dos arquivos, em MB; acima, 413 {@code GEOMETRIAS_GRANDES_DEMAIS}. */
    long maximoMb();

    /** Quantidade máxima de arquivos num ZIP; acima, 413 {@code GEOMETRIAS_GRANDES_DEMAIS}. */
    int maximoDeArquivos();

    /** Espaço livre mínimo antes de cada download, em MB (o mesmo do dossiê). */
    long espacoMinimoMb();

    /** Diretório temporário; cada download usa uma subpasta própria, apagada ao fim (subdiretório de {@code VISTAS_DOSSIE_DIRETORIO}). */
    Path diretorio();
}
