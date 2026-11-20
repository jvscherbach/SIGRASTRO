package br.gov.mt.sema.rastro.shared.application.exception;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;

/**
 * O pedido está concluído, mas o arquivo final não está mais em condição de ser entregue (S6, bloco 3): sumiu do disco
 * (410 {@code DOSSIE_ARQUIVO_AUSENTE}) ou está vazio ou com tamanho diferente do gravado (500
 * {@code DOSSIE_ARQUIVO_INVALIDO}). Nunca 200 com corpo vazio ou truncado. A mensagem não traz caminho.
 */
public class ArquivoDoDossieIndisponivelException extends RastroException {

    public static final String MENSAGEM_AUSENTE =
            "O arquivo do dossiê não está mais no servidor. Gere o dossiê novamente.";
    public static final String MENSAGEM_INVALIDO =
            "O arquivo do dossiê está incompleto no servidor (por exemplo, por falta de espaço em disco). "
                    + "Libere espaço e gere o dossiê novamente.";

    private ArquivoDoDossieIndisponivelException(ErrorCode codigo, String mensagem) {
        super(codigo, mensagem);
    }

    public static ArquivoDoDossieIndisponivelException ausente() {
        return new ArquivoDoDossieIndisponivelException(ErrorCode.DOSSIE_ARQUIVO_AUSENTE, MENSAGEM_AUSENTE);
    }

    public static ArquivoDoDossieIndisponivelException invalido() {
        return new ArquivoDoDossieIndisponivelException(ErrorCode.DOSSIE_ARQUIVO_INVALIDO, MENSAGEM_INVALIDO);
    }
}
