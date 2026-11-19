package br.gov.mt.sema.rastro.documento.application.port;

import java.nio.file.Path;

import br.gov.mt.sema.rastro.documento.domain.TipoDetectado;

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
     * @return páginas geradas e o que foi ajustado nas planilhas
     * @throws FalhaDeConversaoException com o motivo tipado, exibível ao usuário
     */
    default ResultadoDaConversao converter(Path origem, TipoDetectado tipo, Path destinoPdf) {
        return converter(origem, tipo, destinoPdf, null);
    }

    /**
     * Como {@link #converter(Path, TipoDetectado, Path)}, com o título do documento para o cabeçalho das páginas de
     * planilha (S3.1): o mesmo texto do sumário e do marcador, sem a nota "convertido de". É dado não confiável: vai ao
     * conversor só como texto, sem caractere de controle e com tamanho limitado. Nulo ou vazio: cabeçalho vazio (nunca o
     * nome da aba sozinho). Documentos de texto ignoram o título.
     */
    ResultadoDaConversao converter(Path origem, TipoDetectado tipo, Path destinoPdf, String titulo);
}
