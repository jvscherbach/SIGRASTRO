package br.gov.mt.sema.vistas.documento.application.port;

import java.nio.file.Path;

import br.gov.mt.sema.vistas.documento.domain.TipoDetectado;

/**
 * Converte imagens PNG e JPEG para PDF (S3), sem LibreOffice: uma página por imagem. Serve ao dossiê e à
 * visualização, com os mesmos parâmetros ({@link ParametrosDaConversao}).
 */
public interface ConversorDeImagens {

    /**
     * Converte {@code origem} para um PDF de uma página em {@code destinoPdf}. O arquivo de origem não é alterado; em
     * qualquer falha nada fica no destino.
     *
     * @throws FalhaDeConversaoException {@code IMAGEM_GRANDE_DEMAIS} acima do limite de megapixels (decidido pelas
     *     dimensões, antes de decodificar), {@code CORROMPIDO} para imagem ilegível e {@code FORMATO_NAO_SUPORTADO}
     *     quando o conteúdo não é PNG nem JPEG
     */
    ResultadoDaConversao converter(Path origem, TipoDetectado tipo, Path destinoPdf);
}
