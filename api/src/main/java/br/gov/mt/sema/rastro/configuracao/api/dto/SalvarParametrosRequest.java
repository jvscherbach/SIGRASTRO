package br.gov.mt.sema.rastro.configuracao.api.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** @param alteracoes todas as alterações pendentes, por nome: número, booleano ou texto */
public record SalvarParametrosRequest(Map<String, Object> alteracoes) {

    /** Cada valor como texto; nulo fica nulo e é recusado na validação. */
    public Map<String, String> emTexto() {
        Map<String, String> textos = new LinkedHashMap<>();
        if (alteracoes != null) {
            alteracoes.forEach((nome, valor) -> textos.put(nome, valor == null ? null : String.valueOf(valor)));
        }
        return textos;
    }
}
