package br.gov.mt.sema.rastro.painel.domain;

import java.util.List;

/** Gráfico 3. {@code nomesOcultos}: o parâmetro de exibição de nomes estava desligado (RN12). */
public record CargaPorResponsavel(List<LinhaDeCarga> linhas, boolean nomesOcultos, List<AvisoDoPainel> avisos) {

    public CargaPorResponsavel {
        linhas = List.copyOf(linhas);
        avisos = List.copyOf(avisos);
    }
}
