package br.gov.mt.sema.vistas.documento.application.port;

import java.nio.file.Path;

import br.gov.mt.sema.vistas.documento.domain.TipoDetectado;

/**
 * Converte planilhas, textos e HTML para PDF (S3), implementada por integration.libreoffice. Imagens não passam por
 * aqui (conversor próprio, por PDFBox). Serve ao dossiê e à visualização, com os mesmos parâmetros
 * ({@link ParametrosDaConversao}).
 *
 * <p>Os documentos são de terceiros e tratados como hostis: a implementação trabalha numa cópia, em diretório próprio
 * criado ao lado do destino, sem executar macro, sem atualizar vínculo externo e sem acesso à rede pelo conteúdo.
 */
public interface ConversorDeDocumentos {

    /** O conversor existe e pode ser usado? Falso quando desligado, ausente ou sem permissão; a aplicação sobe igual. */
    boolean disponivel();

    /**
     * Converte {@code origem} (do tipo detectado pelo conteúdo) para PDF em {@code destinoPdf}. O arquivo de origem
     * não é alterado; em qualquer falha nada fica no destino nem no diretório de trabalho.
     *
     * @throws FalhaDeConversaoException com o motivo tipado, exibível ao usuário
     */
    void converter(Path origem, TipoDetectado tipo, Path destinoPdf);
}
