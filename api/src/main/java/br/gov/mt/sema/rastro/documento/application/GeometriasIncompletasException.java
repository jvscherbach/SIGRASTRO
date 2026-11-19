package br.gov.mt.sema.rastro.documento.application;

import java.util.List;

import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import br.gov.mt.sema.rastro.shared.application.exception.RastroException;

/**
 * Algum arquivo das geometrias não foi obtido: nenhum ZIP é entregue (tudo ou nada, como a RN09). Traz a lista dos
 * arquivos com problema, pelo nome que teriam no ZIP e com o motivo exibível; nunca a chave, a URL ou o token.
 */
public class GeometriasIncompletasException extends RastroException {

    /** @param tempoLimite a falha foi por tempo limite (todas assim: 504; senão, 502) */
    public record Falha(String nome, String motivo, boolean tempoLimite) {
    }

    private final List<Falha> falhas;

    public GeometriasIncompletasException(List<Falha> falhas, int total) {
        super(ErrorCode.GEOMETRIAS_INCOMPLETAS, "Não foi possível obter " + falhas.size() + " de " + total
                + " geometria(s). Nenhum arquivo foi entregue: tente novamente.");
        this.falhas = List.copyOf(falhas);
    }

    public List<Falha> falhas() {
        return falhas;
    }

    public boolean soTempoLimite() {
        return falhas.stream().allMatch(Falha::tempoLimite);
    }
}
