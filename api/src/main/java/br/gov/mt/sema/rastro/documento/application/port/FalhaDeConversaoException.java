package br.gov.mt.sema.rastro.documento.application.port;

/**
 * Falha tipada da conversão para PDF. É falha do documento (S2.2: coletada, decisão do usuário), nunca da geração
 * inteira. A mensagem é o motivo exibido ao usuário, em português, sem caminho interno, nome de arquivo nem dado do
 * documento.
 */
public class FalhaDeConversaoException extends RuntimeException {

    public enum Motivo {
        PROTEGIDO_POR_SENHA("documento protegido por senha"),
        CORROMPIDO("arquivo corrompido ou ilegível pelo conversor"),
        TEMPO_LIMITE("tempo limite da conversão excedido"),
        CONVERSOR_INDISPONIVEL("conversor de documentos indisponível"),
        SAIDA_VAZIA("a conversão não gerou páginas"),
        SAIDA_GRANDE_DEMAIS("o PDF convertido excede o tamanho máximo permitido"),
        ENTRADA_GRANDE_DEMAIS("arquivo grande demais para a conversão"),
        INTERROMPIDA("conversão interrompida"),
        PLANILHA_VAZIA("planilha sem conteúdo imprimível"),
        PLANILHA_INCOMPLETA("a planilha não coube inteira no PDF: as últimas linhas não aparecem"),
        IMAGEM_GRANDE_DEMAIS("imagem com resolução acima do limite permitido"),
        PAGINAS_DEMAIS("o PDF convertido excede o número máximo de páginas permitido"),
        FORMATO_NAO_SUPORTADO("formato sem conversão para PDF");

        private final String mensagem;

        Motivo(String mensagem) {
            this.mensagem = mensagem;
        }

        public String mensagem() {
            return mensagem;
        }
    }

    private final Motivo motivo;

    public FalhaDeConversaoException(Motivo motivo) {
        super(motivo.mensagem());
        this.motivo = motivo;
    }

    public FalhaDeConversaoException(Motivo motivo, Throwable causa) {
        super(motivo.mensagem(), causa);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
