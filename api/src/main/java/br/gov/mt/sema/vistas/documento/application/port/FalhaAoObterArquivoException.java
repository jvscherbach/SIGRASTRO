package br.gov.mt.sema.vistas.documento.application.port;

/** Falha tipada ao obter o arquivo do repositório para a visualização. A mensagem não traz chave, URL nem token. */
public class FalhaAoObterArquivoException extends RuntimeException {

    public enum Motivo {
        /** O repositório não tem o arquivo (HTTP 404). */
        NAO_ENCONTRADO,
        /** Tempo de inatividade ou teto total excedido. */
        TEMPO_LIMITE,
        /** Repositório fora do ar, não configurado ou com resposta inesperada. */
        INDISPONIVEL
    }

    private final Motivo motivo;

    public FalhaAoObterArquivoException(Motivo motivo, String mensagem) {
        super(mensagem);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
