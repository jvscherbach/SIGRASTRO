package br.gov.mt.sema.vistas.documento.domain;

/**
 * Resultado da detecção. {@code extensaoSugerida} é a extensão que a conversão deve usar no arquivo de trabalho (a do
 * nome original pode ser enganosa); nula quando o tipo não é convertível nem PDF.
 */
public record TipoDetectado(TipoReal tipo, String extensaoSugerida) {

    static final TipoDetectado DESCONHECIDO = new TipoDetectado(TipoReal.DESCONHECIDO, null);

    public boolean convertivel() {
        return tipo == TipoReal.PLANILHA || tipo == TipoReal.TEXTO || tipo == TipoReal.IMAGEM;
    }
}
